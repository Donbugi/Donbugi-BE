package hongik.finEdu.points.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.points.dto.policy.AttendanceCheckInRequest;
import hongik.finEdu.points.dto.policy.AttendanceCheckInResponse;
import hongik.finEdu.points.dto.policy.AttendanceStatusResponse;
import hongik.finEdu.points.dto.policy.DailyQuizRewardRequest;
import hongik.finEdu.points.dto.policy.DailyQuizRewardResponse;
import hongik.finEdu.points.dto.policy.NewsDetailQuizRewardRequest;
import hongik.finEdu.points.dto.policy.NewsDetailQuizRewardResponse;
import hongik.finEdu.points.service.AttendancePointService;
import hongik.finEdu.points.service.PointPolicyRewardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points/rewards")
@RequiredArgsConstructor
public class PointRewardsController {

    private final AttendancePointService attendancePointService;
    private final PointPolicyRewardService pointPolicyRewardService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceCheckInResponse> attendance(
            Authentication authentication,
            @RequestBody AttendanceCheckInRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(attendancePointService.checkIn(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/attendance/status")
    public ResponseEntity<AttendanceStatusResponse> attendanceStatus(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(attendancePointService.getStatus(uid));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/daily-quiz")
    public ResponseEntity<DailyQuizRewardResponse> dailyQuiz(
            Authentication authentication,
            @RequestBody DailyQuizRewardRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(pointPolicyRewardService.rewardDailyQuiz(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/news-detail-quiz")
    public ResponseEntity<NewsDetailQuizRewardResponse> newsDetailQuiz(
            Authentication authentication,
            @RequestBody NewsDetailQuizRewardRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(pointPolicyRewardService.rewardNewsDetailQuiz(request));
    }
}
