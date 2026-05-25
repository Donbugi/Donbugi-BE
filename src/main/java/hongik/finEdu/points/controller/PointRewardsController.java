package hongik.finEdu.points.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.points.dto.policy.AttendanceCheckInRequest;
import hongik.finEdu.points.dto.policy.AttendanceCheckInResponse;
import hongik.finEdu.points.dto.policy.AttendanceStatusResponse;
import hongik.finEdu.points.dto.policy.DailyQuizRewardRequest;
import hongik.finEdu.points.dto.policy.DailyQuizRewardResponse;
import hongik.finEdu.points.dto.policy.NewsDetailQuizRewardRequest;
import hongik.finEdu.points.dto.policy.NewsDetailQuizRewardResponse;
import hongik.finEdu.points.service.AttendancePointService;
import hongik.finEdu.points.service.PointPolicyRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = OpenApiTags.POINTS, description = "정책 기반 포인트 보상")
@RestController
@RequestMapping("/api/points/rewards")
@RequiredArgsConstructor
public class PointRewardsController {

    private final AttendancePointService attendancePointService;
    private final PointPolicyRewardService pointPolicyRewardService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "출석 체크",
            description = """
                    앱 진입 시 1일 1회 호출. 같은 날 재호출 시 alreadyCheckedInForDate=true.
                    연속 7·14·21…일마다 +100P (pointsAwardedThisRequest).""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceCheckInResponse> attendance(
            Authentication authentication,
            @RequestBody AttendanceCheckInRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(attendancePointService.checkIn(request));
    }

    @Operation(
            summary = "출석 현황 조회",
            description = "연속 출석 일수, 오늘 출석 여부, 현재 잔액. 마이페이지 '연속 출석'용.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/attendance/status")
    public ResponseEntity<AttendanceStatusResponse> attendanceStatus(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(attendancePointService.getStatus(uid));
    }

    @Operation(
            summary = "오늘의 과제 보상",
            description = """
                    3슬롯 결과 제출. 슬롯당 참여 +20P, 3문항 전부 참여+전부 정답 시 +20P 보너스.
                    sessionId로 중복 지급 방지.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/daily-quiz")
    public ResponseEntity<DailyQuizRewardResponse> dailyQuiz(
            Authentication authentication,
            @RequestBody DailyQuizRewardRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(pointPolicyRewardService.rewardDailyQuiz(request));
    }

    @Operation(
            summary = "뉴스 상세 퀴즈 보상",
            description = "기사당 1회 +20P (정답 여부 무관). articleId 기준 멱등.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/news-detail-quiz")
    public ResponseEntity<NewsDetailQuizRewardResponse> newsDetailQuiz(
            Authentication authentication,
            @RequestBody NewsDetailQuizRewardRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(pointPolicyRewardService.rewardNewsDetailQuiz(request));
    }
}
