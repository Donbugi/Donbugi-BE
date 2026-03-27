package hongik.finEdu.quiz.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.common.exception.RetryableBusinessException;
import hongik.finEdu.quiz.dto.QuizResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiQuizClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiQuizClient(@Value("${quiz.ai-server-url}") String aiServerUrl,
                         @Value("${quiz.ai-connect-timeout-ms:5000}") int connectTimeout,
                         @Value("${quiz.ai-read-timeout-ms:60000}") int readTimeout,
                         ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(aiServerUrl)
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * AI 서버에 기사 본문을 보내고 퀴즈 JSON을 받아옴.
     * 반환값: quiz 배열만 직렬화한 JSON 문자열
     */
    @Retryable(
            includes = RetryableBusinessException.class,
            maxRetries = 3,
            delay = 2000,
            multiplier = 2
    )
    public String generateQuiz(Long articleId, String content) {
        log.info("[AI 호출] articleId={}", articleId);

        Map<String, Object> body = Map.of(
                "article_id", articleId,
                "content", content
        );

        String response;
        try {
            response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            log.warn("[AI 재시도] articleId={}, 타임아웃: {}", articleId, e.getMessage());
            throw new RetryableBusinessException(ErrorCode.AI_SERVER_ERROR, e.getMessage(), e);
        } catch (RestClientException e) {
            log.warn("[AI 재시도] articleId={}, 서버 오류: {}", articleId, e.getMessage());
            throw new RetryableBusinessException(ErrorCode.AI_SERVER_ERROR, e.getMessage(), e);
        }

        String quizJson = extractQuizArray(response);

        log.info("[AI 응답] articleId={}, length={}", articleId, quizJson.length());
        return quizJson;
    }

    /**
     * AI 응답에서 "quiz" 배열을 추출하고 유효성 검증
     */
    private String extractQuizArray(String response) {
        if (response == null || response.isBlank()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, "빈 응답");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode quizNode = root.get("quiz");

            if (quizNode == null || !quizNode.isArray() || quizNode.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, "quiz 배열이 없거나 비어있음");
            }

            String quizArrayJson = objectMapper.writeValueAsString(quizNode);

            List<QuizResponseDto> quizzes = objectMapper.readValue(
                    quizArrayJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, QuizResponseDto.class)
            );

            return quizArrayJson;
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, e.getMessage(), e);
        }
    }
}
