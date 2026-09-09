package com.fmi.domain.chatmessage.service;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.domain.chatmessage.service.ChatTranslationSourceService.Source;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationRequest.TranslateRequestDTO;
import com.fmi.external.translation.util.TranslationTextNormalizer;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisTranslationQuotaStore {

    public static final int LIMIT = 20;

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_ATTEMPTS = 2;
    private static final int REPLY_SIZE = 7;
    private static final DefaultRedisScript<List> SCRIPT = script();

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    private static DefaultRedisScript<List> script() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/chat-translation.lua"));
        script.setResultType(List.class);
        return script;
    }

    public Ticket reserve(Source source, Long roomId, Long messageId, TranslateRequestDTO request) {
        String fingerprint = TranslationTextNormalizer.hash(
                roomId + ":" + messageId + ":" + request.roomVisitId() + ":" + source.language() + ":" + source.text());
        String token = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            LocalDate day = today();
            Ticket ticket = new Ticket(
                    source.userId(), messageId, request.requestId(), token, day, fingerprint, source.language(), null);
            Outcome outcome = execute("reserve", ticket, "");
            if (outcome.status() == Status.DAY_CHANGED) {
                continue;
            }
            return new Ticket(
                    source.userId(),
                    messageId,
                    request.requestId(),
                    token,
                    outcome.usage().usageDate(),
                    fingerprint,
                    source.language(),
                    outcome);
        }
        throw new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE);
    }

    /**
     * complete() 직후처럼 이미 확정 시점의 사용량을 들고 있을 때 쓴다. 예약일과 오늘이 같으면
     * 그 값을 그대로 돌려주고, 자정을 넘겨 날짜가 달라졌을 때만 Redis를 다시 조회한다.
     */
    public Usage usageAfterCompletion(Long userId, Usage completedUsage) {
        if (completedUsage.usageDate().equals(today())) {
            return completedUsage;
        }
        return usage(userId);
    }

    public Usage usage(Long userId) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // 조회 전용이라 requestId·messageId·언어는 쓰이지 않는다.
            Ticket ticket = new Ticket(userId, 0L, new UUID(0, 0), "", today(), "", LanguageCode.KO, null);
            Outcome outcome = execute("usage", ticket, "");
            if (outcome.status() != Status.DAY_CHANGED) {
                return outcome.usage();
            }
        }
        throw new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE);
    }

    /**
     * 이미 번역해 둔 같은 메시지·같은 대상 언어의 결과를 찾는다.
     */
    public Optional<String> findCached(Ticket ticket) {
        try {
            return Optional.ofNullable(stringRedisTemplate.opsForValue().get(cacheKey(ticket)));
        } catch (DataAccessException e) {
            log.warn("채팅 번역 캐시 조회 실패: messageId={}", ticket.messageId(), e);
            return Optional.empty();
        }
    }

    public Outcome complete(Ticket ticket, String translatedText) {
        return execute("complete", ticket, translatedText);
    }

    public void fail(Ticket ticket) {
        execute("fail", ticket, "");
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private String prefix(Long userId) {
        return "chat:translation:{" + userId + "}:";
    }

    private String cacheKey(Ticket ticket) {
        return prefix(ticket.userId()) + "msg:" + ticket.messageId() + ":" + ticket.language();
    }

    private String visitKey(Ticket ticket) {
        return prefix(ticket.userId()) + "fp:" + ticket.fingerprint();
    }

    private Outcome execute(String operation, Ticket ticket, String text) {
        String prefix = prefix(ticket.userId());
        List<String> keys = List.of(
                prefix + ticket.day() + ":used",
                prefix + ticket.day() + ":pending",
                prefix + "request:" + ticket.requestId(),
                cacheKey(ticket),
                visitKey(ticket));
        Instant start = ticket.day().atStartOfDay(ZONE).toInstant();
        Instant reset = ticket.day().plusDays(1).atStartOfDay(ZONE).toInstant();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                List<?> values = stringRedisTemplate.execute(
                        SCRIPT,
                        keys,
                        operation,
                        ticket.fingerprint(),
                        ticket.token(),
                        ticket.day().toString(),
                        Long.toString(start.toEpochMilli()),
                        Long.toString(reset.toEpochMilli()),
                        ticket.language().name(),
                        text,
                        Integer.toString(LIMIT),
                        Long.toString(CACHE_TTL.toMillis()));
                if (values == null || values.size() != REPLY_SIZE) {
                    throw new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE);
                }
                return toOutcome(values);
            } catch (DataAccessException e) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    log.warn("채팅 번역 Redis 작업 실패: operation={}, requestId={}", operation, ticket.requestId(), e);
                    throw new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE);
                }
            }
        }
        throw new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE);
    }

    private Outcome toOutcome(List<?> values) {
        int used = Integer.parseInt(values.get(1).toString());
        int pending = Integer.parseInt(values.get(2).toString());
        Usage usage = new Usage(
                LocalDate.parse(values.get(3).toString()),
                used,
                LIMIT,
                Instant.ofEpochMilli(Long.parseLong(values.get(4).toString())),
                used + pending < LIMIT);

        return new Outcome(
                Status.valueOf(values.get(0).toString()),
                usage,
                LanguageCode.valueOf(values.get(5).toString()),
                values.get(6).toString());
    }

    public enum Status {
        RESERVED,
        SUCCEEDED,
        FAILED,
        IN_PROGRESS,
        LIMIT,
        CONFLICT,
        DAY_CHANGED,
        USAGE
    }

    public record Usage(LocalDate usageDate, int usedCount, int limit, Instant resetAt, boolean canTranslate) {}

    public record Outcome(Status status, Usage usage, LanguageCode language, String text) {}

    public record Ticket(
            Long userId,
            Long messageId,
            UUID requestId,
            String token,
            LocalDate day,
            String fingerprint,
            LanguageCode language,
            Outcome outcome) {}
}
