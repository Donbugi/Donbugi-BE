package hongik.finEdu.news.interest.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.news.interest.dto.NewsInterestReadRequest;
import hongik.finEdu.news.interest.dto.NewsInterestTrendsResponse;
import hongik.finEdu.news.interest.service.NewsInterestRedisService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news/interest")
@RequiredArgsConstructor
public class NewsInterestController {

    private final NewsInterestRedisService newsInterestRedisService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/read")
    public ResponseEntity<Void> recordRead(
            Authentication authentication,
            @RequestBody NewsInterestReadRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        newsInterestRedisService.recordRead(request);
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/trends")
    public ResponseEntity<NewsInterestTrendsResponse> trends(
            Authentication authentication,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer top) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(newsInterestRedisService.getTrends(uid, top));
    }
}
