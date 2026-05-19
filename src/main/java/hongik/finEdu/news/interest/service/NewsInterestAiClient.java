package hongik.finEdu.news.interest.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import hongik.finEdu.news.interest.dto.NewsInterestTopicDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관심 토픽 목록을 보내 한국어 인사이트 1문장을 받는다.
 * {@code news.interest.ai-insight-url} 이 비어 있으면 호출하지 않는다.
 * <p>
 * AI 서버 예시 요청 본문: {@code { "topics": [ {"name":"반도체","count":12}, ... ] } }<br/>
 * 응답: {@code { "insight": "..." } }
 */
@Slf4j
@Component
public class NewsInterestAiClient {

    private final String insightUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NewsInterestAiClient(
            @Value("${news.interest.ai-insight-url:}") String insightUrl,
            @Value("${news.interest.ai-connect-timeout-ms:5000}") int connectTimeout,
            @Value("${news.interest.ai-read-timeout-ms:45000}") int readTimeout,
            ObjectMapper objectMapper) {
        this.insightUrl = insightUrl == null ? "" : insightUrl.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !insightUrl.isBlank();
    }

    public Optional<String> requestInsight(List<NewsInterestTopicDto> topics) {
        if (!isConfigured() || topics == null || topics.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> list = topics.stream()
                    .map(t -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", t.name());
                        m.put("count", t.count());
                        return m;
                    })
                    .toList();
            String body = objectMapper.writeValueAsString(Map.of("topics", list));

            String response = restClient.post()
                    .uri(insightUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            if (root.hasNonNull("insight")) {
                String text = root.get("insight").asText().trim();
                if (!text.isEmpty()) {
                    return Optional.of(text);
                }
            }
        } catch (JacksonException | RestClientException e) {
            log.warn("[뉴스 관심 AI] 호출 실패: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[뉴스 관심 AI] 오류: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
