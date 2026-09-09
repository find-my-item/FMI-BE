package com.fmi.external.translation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.global.apiPayload.code.ErrorReasonDTO;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DeepLTranslationClientTest {

    private static final String BASE_URL = "https://api-free.deepl.com/v2/translate";
    private static final String API_KEY = "test-api-key";

    private DeepLTranslationClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new DeepLTranslationClient(new DeepLProperties(API_KEY, BASE_URL));
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("번역에 성공하면 첫 번째 번역 결과 텍스트를 반환한다")
    void returnsFirstTranslatedText() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + API_KEY))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("target_lang=EN-US")))
                .andRespond(withSuccess(
                        "{\"translations\":[{\"detected_source_language\":\"KO\",\"text\":\"I lost my wallet\"}]}",
                        MediaType.APPLICATION_JSON));

        String result = client.doTranslate("지갑을 잃어버렸어요", LanguageCode.EN);

        assertThat(result).isEqualTo("I lost my wallet");
        server.verify();
    }

    @Test
    @DisplayName("대상 언어가 KO면 target_lang=KO로 요청한다")
    void sendsKoreanTargetLanguage() {
        server.expect(requestTo(BASE_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("target_lang=KO")))
                .andRespond(withSuccess("{\"translations\":[{\"text\":\"지갑을 잃어버렸어요\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.doTranslate("I lost my wallet", LanguageCode.KO)).isEqualTo("지갑을 잃어버렸어요");
        server.verify();
    }

    @Test
    @DisplayName("번역 대상 텍스트를 요청 본문에 담아 보낸다")
    void sendsTextInRequestBody() {
        server.expect(requestTo(BASE_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("text=wallet")))
                .andRespond(withSuccess("{\"translations\":[{\"text\":\"지갑\"}]}", MediaType.APPLICATION_JSON));

        client.doTranslate("wallet", LanguageCode.KO);

        server.verify();
    }

    @Test
    @DisplayName("200이지만 응답 본문이 비어 있으면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
    void throwsWhenResponseBodyIsEmpty() {
        server.expect(requestTo(BASE_URL)).andRespond(withStatus(HttpStatus.OK));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("200이지만 응답에 translations 필드가 없으면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
    void throwsWhenTranslationsFieldIsMissing() {
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("200이지만 translations가 빈 배열이면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
    void throwsWhenTranslationsIsEmpty() {
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("{\"translations\":[]}", MediaType.APPLICATION_JSON));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("200이지만 번역 결과가 공백뿐이면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
    void throwsWhenTranslatedTextIsBlank() {
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess("{\"translations\":[{\"text\":\" \"}]}", MediaType.APPLICATION_JSON));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("200이지만 번역 결과에 text 필드가 없으면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
    void throwsWhenTranslatedTextIsMissing() {
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess("{\"translations\":[{}]}", MediaType.APPLICATION_JSON));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("200이지만 translations 원소가 null이면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
    void throwsWhenTranslationEntryIsNull() {
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess("{\"translations\":[null]}", MediaType.APPLICATION_JSON));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("500을 반환하면 RestClientException을 GeneralException(TRANSLATION500-API_ERROR)으로 변환한다")
    void throwsWhenServerRespondsWithError() {
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    @Test
    @DisplayName("403을 반환하면 RestClientException을 GeneralException(TRANSLATION500-API_ERROR)으로 변환한다")
    void throwsWhenAuthenticationFails() {
        server.expect(requestTo(BASE_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertTranslationApiError(() -> client.doTranslate("지갑", LanguageCode.EN));
    }

    /** 번역 실패 시 클라이언트가 던지는 예외의 타입과 응답 페이로드 형식을 함께 검증한다. */
    private void assertTranslationApiError(Runnable runnable) {
        assertThatExceptionOfType(GeneralException.class)
                .isThrownBy(runnable::run)
                .satisfies(exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorStatus._TRANSLATION_API_ERROR);

                    ErrorReasonDTO reason = exception.getErrorReasonHttpStatus();
                    assertThat(reason.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(reason.getCode()).isEqualTo("TRANSLATION500-API_ERROR");
                    assertThat(reason.getMessage()).isEqualTo("번역 요청에 실패했습니다. 잠시 후 다시 시도해주세요.");
                    assertThat(reason.getIsSuccess()).isFalse();
                });

        server.verify();
    }
}
