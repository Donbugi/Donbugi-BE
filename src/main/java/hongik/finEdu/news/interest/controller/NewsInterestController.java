package hongik.finEdu.news.interest.controller;

import hongik.finEdu.news.interest.dto.NewsInterestReadRequest;
import hongik.finEdu.news.interest.dto.NewsInterestTrendsResponse;
import hongik.finEdu.news.interest.service.NewsInterestRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news/interest")
@RequiredArgsConstructor
public class NewsInterestController {

    private final NewsInterestRedisService newsInterestRedisService;

    /**
     * 뉴스 조회(읽음) 1회 기록 — 이번 달(Asia/Seoul) 카테고리별 횟수 Redis HINCRBY.
     * category: 기사 category(금융·증권 등) 또는 프론트에서 정한 토픽 라벨
     */
    @PostMapping("/read")
    public ResponseEntity<Void> recordRead(@RequestBody NewsInterestReadRequest request) {
        newsInterestRedisService.recordRead(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 최근 한 달 상위 토픽 + AI 한마디
     * GET /api/news/interest/trends?userId=&top=5
     */
    @GetMapping("/trends")
    public ResponseEntity<NewsInterestTrendsResponse> trends(
            @RequestParam String userId,
            @RequestParam(required = false) Integer top) {
        return ResponseEntity.ok(newsInterestRedisService.getTrends(userId, top));
    }
}
