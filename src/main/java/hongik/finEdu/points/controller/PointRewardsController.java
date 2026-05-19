package hongik.finEdu.points.controller;

import hongik.finEdu.points.dto.policy.*;
import hongik.finEdu.points.service.AttendancePointService;
import hongik.finEdu.points.service.PointPolicyRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/points/rewards")
@RequiredArgsConstructor
public class PointRewardsController {

    private final AttendancePointService attendancePointService;
    private final PointPolicyRewardService pointPolicyRewardService;

    /** FE에서 매일 호출 — 연속 7일마다 100P (멱등: 같은 날 2번 출석 처리 시 스킵) */
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceCheckInResponse> attendance(
            @RequestBody AttendanceCheckInRequest request) {
        return ResponseEntity.ok(attendancePointService.checkIn(request));
    }

    /** 오늘의 과제 3슬롯: 참여 슬롯당 20P, 전부 참여+전부 정답 시 +20P */
    @PostMapping("/daily-quiz")
    public ResponseEntity<DailyQuizRewardResponse> dailyQuiz(@RequestBody DailyQuizRewardRequest request) {
        return ResponseEntity.ok(pointPolicyRewardService.rewardDailyQuiz(request));
    }

    /** 뉴스 상세 퀴즈: 기사당 참여만 해도 20P (정답 여부 무관) */
    @PostMapping("/news-detail-quiz")
    public ResponseEntity<NewsDetailQuizRewardResponse> newsDetailQuiz(
            @RequestBody NewsDetailQuizRewardRequest request) {
        return ResponseEntity.ok(pointPolicyRewardService.rewardNewsDetailQuiz(request));
    }
}
