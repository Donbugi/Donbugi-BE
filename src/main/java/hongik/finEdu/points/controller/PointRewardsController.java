package hongik.finEdu.points.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.points.dto.policy.AttendanceCheckInRequest;
import hongik.finEdu.points.dto.policy.AttendanceCheckInResponse;
import hongik.finEdu.points.dto.policy.AttendanceStatusResponse;
import hongik.finEdu.points.service.AttendancePointService;
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
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "출석 체크",
            description = """
                    앱 진입 시 1일 1회 호출. 같은 날 재호출 시 alreadyCheckedInForDate=true.
                    연속 7·14·21…일마다 +100P (pointsAwardedThisRequest).
                    출석일은 서버 기준 오늘이며, body 의 date 는 무시된다.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceCheckInResponse> attendance(
            Authentication authentication,
            @RequestBody(required = false) AttendanceCheckInRequest request) {
        String uid = authUserResolver.requireMatchingUserId(authentication, request == null ? null : request.userId());
        // 출석일은 서버가 결정한다 (클라이언트 date 무시)
        return ResponseEntity.ok(attendancePointService.checkIn(new AttendanceCheckInRequest(uid, null)));
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

    /**
     * 410 Gone — 클라이언트 자기신고 방식이라 어뷰징 가능했던 API.
     * 대체: GET /api/quiz/daily + POST /api/quiz/daily/answer (서버 채점)
     */
    @Operation(summary = "[폐기] 오늘의 과제 보상", description = "410. 대체: POST /api/quiz/daily/answer", deprecated = true)
    @PostMapping("/daily-quiz")
    public ResponseEntity<Void> dailyQuiz() {
        throw new BusinessException(ErrorCode.ENDPOINT_GONE, "POST /api/quiz/daily/answer 를 사용하세요.");
    }

    /**
     * 410 Gone — 퀴즈를 풀지 않아도 articleId 만으로 보상 수령 가능했던 API.
     * 대체: POST /api/quiz/{articleId}/answer (서버 채점 + 참여 보상)
     */
    @Operation(summary = "[폐기] 뉴스 상세 퀴즈 보상", description = "410. 대체: POST /api/quiz/{articleId}/answer", deprecated = true)
    @PostMapping("/news-detail-quiz")
    public ResponseEntity<Void> newsDetailQuiz() {
        throw new BusinessException(ErrorCode.ENDPOINT_GONE, "POST /api/quiz/{articleId}/answer 를 사용하세요.");
    }
}
