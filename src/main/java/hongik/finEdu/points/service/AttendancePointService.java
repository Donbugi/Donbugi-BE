package hongik.finEdu.points.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.AttendanceCheckInRequest;
import hongik.finEdu.points.dto.policy.AttendanceCheckInResponse;
import hongik.finEdu.points.entity.AttendanceDay;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.policy.PointPolicy;
import hongik.finEdu.points.repository.AttendanceDayRepository;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AttendancePointService {

    private final AttendanceDayRepository attendanceDayRepository;
    private final PointRewardService pointRewardService;
    private final PointAccountRepository accountRepository;

    @Value("${app.points.timezone:Asia/Seoul}")
    private String timezoneId;

    @Transactional
    public AttendanceCheckInResponse checkIn(AttendanceCheckInRequest request) {
        String userId = requireUserId(request.userId());
        ZoneId z = ZoneId.of(timezoneId);
        LocalDate today = LocalDate.now(z);
        LocalDate d = request.date() != null ? request.date() : today;
        if (d.isAfter(today)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출석일은 오늘 이후일 수 없습니다.");
        }

        if (attendanceDayRepository.existsByUserIdAndAttendanceDate(userId, d)) {
            int streak = computeStreak(userId, d);
            int bal = accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
            return new AttendanceCheckInResponse(streak, 0, true, bal);
        }

        attendanceDayRepository.save(AttendanceDay.builder()
                .userId(userId)
                .attendanceDate(d)
                .build());

        int streak = computeStreak(userId, d);
        int awarded = 0;
        if (streak >= PointPolicy.ATTENDANCE_STREAK_LENGTH
                && streak % PointPolicy.ATTENDANCE_STREAK_LENGTH == 0) {
            String claimKey = "attendance:streak7:" + userId + ":" + d;
            PointAwardResult r = pointRewardService.tryAwardOnce(
                    userId,
                    claimKey,
                    PointPolicy.ATTENDANCE_STREAK_REWARD,
                    "연속 출석 " + streak + "일 달성",
                    "연속 출석 보상 (" + PointPolicy.ATTENDANCE_STREAK_LENGTH + "일 단위)");
            if (r.awarded()) {
                awarded = r.amount();
            }
        }

        int bal = accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
        return new AttendanceCheckInResponse(streak, awarded, false, bal);
    }

    private int computeStreak(String userId, LocalDate fromDay) {
        int n = 0;
        LocalDate d = fromDay;
        while (attendanceDayRepository.existsByUserIdAndAttendanceDate(userId, d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }
}
