package hongik.finEdu.news.interest.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.news.interest.dto.NewsInterestReadRequest;
import hongik.finEdu.news.interest.dto.NewsInterestTrendsResponse;
import hongik.finEdu.news.interest.service.NewsInterestRedisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = OpenApiTags.NEWS, description = "관심 뉴스·읽음 통계")
@RestController
@RequestMapping("/api/news/interest")
@RequiredArgsConstructor
public class NewsInterestController {

    private final NewsInterestRedisService newsInterestRedisService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "뉴스 읽음 기록",
            description = """
                    기사 1회 읽을 때 호출. 이번 달 카테고리별 Redis 카운트 증가.
                    오늘 5·10·15…회 읽으면 자동 +20P (point_history 반영).""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/read")
    public ResponseEntity<Void> recordRead(
            Authentication authentication,
            @RequestBody NewsInterestReadRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        newsInterestRedisService.recordRead(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "관심 뉴스 동향 + AI 한마디",
            description = "최근 한 달 상위 토픽 태그 + AI insight (finEdu_AI 연동). top 기본 5.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/trends")
    public ResponseEntity<NewsInterestTrendsResponse> trends(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId,
            @Parameter(description = "상위 N개 토픽", example = "5")
            @RequestParam(required = false) Integer top) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(newsInterestRedisService.getTrends(uid, top));
    }
}
