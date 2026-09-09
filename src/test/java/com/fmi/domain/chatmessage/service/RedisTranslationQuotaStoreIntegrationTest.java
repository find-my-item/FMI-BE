package com.fmi.domain.chatmessage.service;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.domain.chatmessage.service.ChatTranslationSourceService.Source;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Outcome;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Status;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Ticket;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationRequest.TranslateRequestDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 redis-server 프로세스를 띄워 Lua 스크립트의 원자성을 검증한다.
 * 로컬에 redis-server가 있어야 하므로 CHAT_TRANSLATION_REDIS_TESTS=true 일 때만 실행된다.
 */
@EnabledIfEnvironmentVariable(named = "CHAT_TRANSLATION_REDIS_TESTS", matches = "true")
class RedisTranslationQuotaStoreIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Long ROOM_ID = 2L;
    private static final Long MESSAGE_ID = 3L;
    private static final String ORIGINAL_TEXT = "지갑 보셨나요";
    private static final String TRANSLATED_TEXT = "Have you seen my wallet";

    @TempDir
    static Path directory;

    private static Process server;
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private static RedisTranslationQuotaStore store;

    private Source source;

    @BeforeAll
    static void startRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new ProcessBuilder(
                        System.getenv().getOrDefault("REDIS_SERVER_EXECUTABLE", "redis-server"),
                        "--bind",
                        "127.0.0.1",
                        "--port",
                        Integer.toString(port),
                        "--save",
                        "",
                        "--appendonly",
                        "no")
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("redis.log").toFile())
                .start();
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (true) {
            try (RedisConnection client = connection.getConnection()) {
                client.ping();
                break;
            } catch (RuntimeException e) {
                if (System.nanoTime() >= deadline) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
        store = new RedisTranslationQuotaStore(redis, Clock.systemUTC());
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connection != null) {
            connection.destroy();
        }
        if (server != null) {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) {
                server.destroyForcibly();
            }
        }
    }

    /** 테스트마다 새 계정을 써서 일일 한도 키가 서로 간섭하지 않도록 한다. */
    @BeforeEach
    void useUniqueAccount() {
        source =
                new Source(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE, ORIGINAL_TEXT, LanguageCode.EN);
    }

    private TranslateRequestDTO newRequest() {
        return new TranslateRequestDTO(UUID.randomUUID(), UUID.randomUUID());
    }

    private Ticket reserve() {
        return store.reserve(source, ROOM_ID, MESSAGE_ID, newRequest());
    }

    private String keyPrefix() {
        return "chat:translation:{" + source.userId() + "}:";
    }

    @Nested
    @DisplayName("일일 한도")
    class DailyLimit {

        @Test
        @DisplayName("동시 요청이 몰려도 하루 성공 번역은 한도(20건)를 넘지 않는다")
        void concurrentRequestsCannotExceedDailyLimit() throws Exception {
            for (int i = 0; i < RedisTranslationQuotaStore.LIMIT - 1; i++) {
                store.complete(reserve(), TRANSLATED_TEXT);
            }

            ExecutorService executor = Executors.newFixedThreadPool(12);
            try {
                List<Callable<Ticket>> calls = new ArrayList<>();
                for (int i = 0; i < 40; i++) {
                    calls.add(RedisTranslationQuotaStoreIntegrationTest.this::reserve);
                }

                List<Ticket> accepted = new ArrayList<>();
                for (Future<Ticket> future : executor.invokeAll(calls)) {
                    Ticket ticket = future.get();
                    if (ticket.outcome().status() == Status.RESERVED) {
                        accepted.add(ticket);
                    }
                }

                assertThat(accepted).hasSize(1);
                assertThat(store.usage(source.userId()).usedCount()).isEqualTo(19);
                assertThat(store.usage(source.userId()).canTranslate()).isFalse();

                store.complete(accepted.get(0), TRANSLATED_TEXT);

                assertThat(store.usage(source.userId()).usedCount()).isEqualTo(RedisTranslationQuotaStore.LIMIT);
                assertThat(reserve().outcome().status()).isEqualTo(Status.LIMIT);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("한도는 채팅방이 아니라 계정 단위로 공유된다")
        void quotaIsSharedAcrossRoomsButNotAccounts() {
            for (int i = 0; i < RedisTranslationQuotaStore.LIMIT; i++) {
                store.complete(reserve(), TRANSLATED_TEXT);
            }

            assertThat(store.reserve(source, 9L, 10L, newRequest()).outcome().status())
                    .isEqualTo(Status.LIMIT);

            Source otherAccount = new Source(source.userId() + 1, ORIGINAL_TEXT, LanguageCode.EN);
            assertThat(store.reserve(otherAccount, ROOM_ID, MESSAGE_ID, newRequest())
                            .outcome()
                            .status())
                    .isEqualTo(Status.RESERVED);
        }

        @Test
        @DisplayName("같은 메시지라도 새 requestId로 다시 요청하면 한도를 다시 차감한다")
        void newRequestIdChargesAgain() {
            store.complete(reserve(), TRANSLATED_TEXT);
            store.complete(reserve(), TRANSLATED_TEXT);

            assertThat(store.usage(source.userId()).usedCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("성공한 requestId를 재요청하면 같은 결과를 돌려주고 뒤늦은 보상은 성공을 되돌리지 못한다")
        void replayReturnsSameResultAndLateCompensationCannotUndoSuccess() {
            TranslateRequestDTO request = newRequest();
            Ticket ticket = store.reserve(source, ROOM_ID, MESSAGE_ID, request);
            store.complete(ticket, TRANSLATED_TEXT);
            store.complete(ticket, "duplicate");
            store.fail(ticket);

            Outcome replay = store.reserve(source, ROOM_ID, MESSAGE_ID, request).outcome();

            assertThat(replay.status()).isEqualTo(Status.SUCCEEDED);
            assertThat(replay.text()).isEqualTo(TRANSLATED_TEXT);
            assertThat(store.usage(source.userId()).usedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패 처리는 여러 번 호출해도 안전하며 예약한 여력을 되돌려준다")
        void failureIsIdempotentAndReleasesCapacity() {
            Ticket ticket = reserve();
            store.fail(ticket);
            store.fail(ticket);

            assertThat(store.complete(ticket, "late").status()).isEqualTo(Status.FAILED);
            assertThat(store.usage(source.userId()).usedCount()).isZero();
            assertThat(reserve().outcome().status()).isEqualTo(Status.RESERVED);
        }

        @Test
        @DisplayName("실패로 끝난 requestId는 재요청해도 FAILED로 남고, 새 requestId로만 다시 번역할 수 있다")
        void failedRequestIdStaysFailedAndOnlyNewRequestIdCanRetry() {
            TranslateRequestDTO request = newRequest();
            store.fail(store.reserve(source, ROOM_ID, MESSAGE_ID, request));

            assertThat(store.reserve(source, ROOM_ID, MESSAGE_ID, request)
                            .outcome()
                            .status())
                    .isEqualTo(Status.FAILED);
            assertThat(reserve().outcome().status()).isEqualTo(Status.RESERVED);
        }

        @Test
        @DisplayName("아직 진행 중인 requestId를 재요청하면 새 예약을 잡지 않는다")
        void duplicatePendingRequestDoesNotAcquireAnotherReservation() {
            TranslateRequestDTO request = newRequest();
            store.reserve(source, ROOM_ID, MESSAGE_ID, request);

            assertThat(store.reserve(source, ROOM_ID, MESSAGE_ID, request)
                            .outcome()
                            .status())
                    .isEqualTo(Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("메시지·방문·대상 언어가 다른데 같은 requestId를 재사용하면 CONFLICT로 거절한다")
        void reusedRequestIdWithDifferentPayloadIsRejected() {
            TranslateRequestDTO request = newRequest();
            store.complete(store.reserve(source, ROOM_ID, MESSAGE_ID, request), TRANSLATED_TEXT);

            assertThat(store.reserve(source, ROOM_ID, 4L, request).outcome().status())
                    .isEqualTo(Status.CONFLICT);
            assertThat(store.reserve(
                                    source,
                                    ROOM_ID,
                                    MESSAGE_ID,
                                    new TranslateRequestDTO(request.requestId(), UUID.randomUUID()))
                            .outcome()
                            .status())
                    .isEqualTo(Status.CONFLICT);
            assertThat(store.reserve(
                                    new Source(source.userId(), ORIGINAL_TEXT, LanguageCode.KO),
                                    ROOM_ID,
                                    MESSAGE_ID,
                                    request)
                            .outcome()
                            .status())
                    .isEqualTo(Status.CONFLICT);
        }
    }

    @Nested
    @DisplayName("예약 만료와 날짜 경계")
    class ExpiryAndDateBoundary {

        @Test
        @DisplayName("만료된 예약은 여력에서 제외되고 뒤늦은 확정은 한도를 차감하지 못한다")
        void expiredReservationIsExcludedAndLateCompletionCannotCharge() {
            Ticket ticket = reserve();
            redis.opsForZSet().add(keyPrefix() + ticket.day() + ":pending", ticket.token(), 0);
            redis.opsForHash().put(keyPrefix() + "request:" + ticket.requestId(), "deadline", "0");

            assertThat(store.usage(source.userId()).canTranslate()).isTrue();
            assertThat(redis.opsForZSet().zCard(keyPrefix() + ticket.day() + ":pending"))
                    .isZero();
            assertThat(store.complete(ticket, "late").status()).isEqualTo(Status.FAILED);
            assertThat(store.usage(source.userId()).usedCount()).isZero();
        }

        @Test
        @DisplayName("자정 직전에 예약한 요청은 예약 당일에 차감되고, 날짜가 바뀌어도 재요청은 같은 결과를 돌려준다")
        void midnightCompletionUsesReservedDayAndReplaySurvivesDateChange() {
            TranslateRequestDTO request = newRequest();
            Ticket current = store.reserve(source, ROOM_ID, MESSAGE_ID, request);
            LocalDate yesterday = current.day().minusDays(1);

            // 자정까지 기다리지 않고, 자정 직전에 예약된 요청 상태를 그대로 흉내 낸다.
            redis.rename(keyPrefix() + current.day() + ":used", keyPrefix() + yesterday + ":used");
            redis.rename(keyPrefix() + current.day() + ":pending", keyPrefix() + yesterday + ":pending");
            String requestKey = keyPrefix() + "request:" + request.requestId();
            redis.opsForHash().put(requestKey, "day", yesterday.toString());
            redis.opsForHash()
                    .put(
                            requestKey,
                            "reset",
                            Long.toString(
                                    current.day().atStartOfDay(ZONE).toInstant().toEpochMilli()));

            Ticket reservedYesterday = new Ticket(
                    current.userId(),
                    current.messageId(),
                    current.requestId(),
                    current.token(),
                    yesterday,
                    current.fingerprint(),
                    current.language(),
                    current.outcome());
            Outcome completed = store.complete(reservedYesterday, TRANSLATED_TEXT);

            assertThat(completed.usage().usageDate()).isEqualTo(yesterday);
            assertThat(completed.usage().usedCount()).isEqualTo(1);
            assertThat(store.usage(source.userId()).usedCount()).isZero();
            assertThat(store.reserve(source, ROOM_ID, MESSAGE_ID, request)
                            .outcome()
                            .status())
                    .isEqualTo(Status.SUCCEEDED);
            assertThat(store.usage(source.userId()).resetAt().atZone(ZONE).getHour())
                    .isZero();
        }
    }

    @Nested
    @DisplayName("번역 캐시")
    class TranslationCache {

        private String cacheKey(LanguageCode language) {
            return keyPrefix() + "msg:" + MESSAGE_ID + ":" + language;
        }

        @Test
        @DisplayName("확정과 함께 번역을 보관해, 새 requestId로 다시 요청해도 DeepL 없이 재사용할 수 있다")
        void completedTranslationIsReusableByLaterRequests() {
            store.complete(reserve(), TRANSLATED_TEXT);

            Ticket reentry = store.reserve(source, ROOM_ID, MESSAGE_ID, newRequest());

            assertThat(store.findCached(reentry)).contains(TRANSLATED_TEXT);
        }

        @Test
        @DisplayName("대상 언어가 다르면 보관된 번역을 쓰지 않는다")
        void cacheIsScopedByTargetLanguage() {
            store.complete(reserve(), TRANSLATED_TEXT);

            Source korean = new Source(source.userId(), ORIGINAL_TEXT, LanguageCode.KO);
            Ticket other = store.reserve(korean, ROOM_ID, MESSAGE_ID, newRequest());

            assertThat(store.findCached(other)).isEmpty();
        }

        @Test
        @DisplayName("보관 기간은 7일이다")
        void cacheIsKeptForSevenDays() {
            store.complete(reserve(), TRANSLATED_TEXT);

            Long ttl = redis.getExpire(cacheKey(LanguageCode.EN), TimeUnit.SECONDS);

            assertThat(ttl)
                    .isBetween(
                            Duration.ofDays(7).toSeconds() - 60,
                            Duration.ofDays(7).toSeconds());
        }

        @Test
        @DisplayName("실패한 번역은 보관하지 않는다")
        void failedTranslationIsNotCached() {
            Ticket ticket = reserve();
            store.fail(ticket);

            assertThat(store.findCached(ticket)).isEmpty();
            assertThat(redis.hasKey(cacheKey(LanguageCode.EN))).isFalse();
        }
    }

    @Nested
    @DisplayName("Redis 응답 유실 복구")
    class LostReplyRecovery {

        @Test
        @DisplayName("예약 응답이 유실돼 재시도해도 같은 예약을 이어받아 여력이 새지 않는다")
        void lostReservationReplyRetriesWithSameOwner() {
            LossyRedisTemplate lossy = new LossyRedisTemplate("reserve");
            RedisTranslationQuotaStore recovering = new RedisTranslationQuotaStore(lossy, Clock.systemUTC());

            Ticket ticket = recovering.reserve(source, ROOM_ID, MESSAGE_ID, newRequest());

            assertThat(lossy.lost).isTrue();
            assertThat(ticket.outcome().status()).isEqualTo(Status.RESERVED);
            assertThat(redis.opsForZSet().zCard(keyPrefix() + ticket.day() + ":pending"))
                    .isEqualTo(1);

            recovering.complete(ticket, TRANSLATED_TEXT);

            assertThat(store.usage(source.userId()).usedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("확정 응답이 유실돼 재시도해도 한도를 두 번 차감하지 않는다")
        void lostCompletionReplyIsRecoveredWithoutDoubleCharging() {
            LossyRedisTemplate lossy = new LossyRedisTemplate("complete");
            RedisTranslationQuotaStore recovering = new RedisTranslationQuotaStore(lossy, Clock.systemUTC());
            Ticket ticket = recovering.reserve(source, ROOM_ID, MESSAGE_ID, newRequest());

            assertThat(recovering.complete(ticket, TRANSLATED_TEXT).status()).isEqualTo(Status.SUCCEEDED);
            assertThat(lossy.lost).isTrue();
            assertThat(store.usage(source.userId()).usedCount()).isEqualTo(1);
        }
    }

    /** 스크립트는 실행됐지만 응답만 유실된 상황을 재현한다. */
    private static class LossyRedisTemplate extends StringRedisTemplate {

        private final String operation;
        private boolean lost;

        LossyRedisTemplate(String operation) {
            super(connection);
            this.operation = operation;
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            T result = super.execute(script, keys, args);
            if (!lost && operation.equals(args[0])) {
                lost = true;
                throw new RedisConnectionFailureException("Simulated lost reply after execution");
            }
            return result;
        }
    }
}
