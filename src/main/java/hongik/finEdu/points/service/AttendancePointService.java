package hongik.finEdu.points.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.AttendanceCheckInRequest;
import hongik.finEdu.points.dto.policy.AttendanceCheckInResponse;
import hongik.finEdu.points.dto.policy.AttendanceStatusResponse;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.policy.PointPolicy;
import hongik.finEdu.points.repository.AttendanceDayRepository;
import hongik.finEdu.points.repository.PointAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendancePointService {

    /** 연속 출석 계산 시 조회할 최대 일수 */
    static final int STREAK_LOOKBACK_DAYS = 400;

    private final AttendanceDayRepository attendanceDayRepository;
    private final PointRewardService pointRewardService;
    private final PointAccountRepository accountRepository;

    @Value("${app.points.timezone:Asia/Seoul}")
    private String timezoneId;

    /**
     * 출석 체크. 출석일은 항상 <b>서버 기준 오늘</b>이다.
     * (과거 date 를 받아 소급 출석 → 연속출석 보상을 반복 수령하던 어뷰징 차단. request.date 는 무시)
     */
    @Transactional
    public AttendanceCheckInResponse checkIn(AttendanceCheckInRequest request) {
        String userId = requireUserId(request.userId());
        LocalDate today = LocalDate.now(ZoneId.of(timezoneId));
        if (request.date() != null && !request.date().equals(today)) {
            log.info("[attendance] 클라이언트 date={} 무시 (서버 today={}) userId={}", request.date(), today, userId);
        }

        int inserted = attendanceDayRepository.insertIfAbsent(userId, today, LocalDateTime.now());
        int streak = computeStreak(userId, today);
        if (inserted == 0) {
            return new AttendanceCheckInResponse(streak, 0, true, balanceOf(userId));
        }

        int awarded = 0;
        if (streak >= PointPolicy.ATTENDANCE_STREAK_LENGTH
                && streak % PointPolicy.ATTENDANCE_STREAK_LENGTH == 0) {
            String claimKey = "attendance:streak7:" + userId + ":" + today;
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
        return new AttendanceCheckInResponse(streak, awarded, false, balanceOf(userId));
    }

    @Transactional(readOnly = true)
    public AttendanceStatusResponse getStatus(String userId) {
        String uid = requireUserId(userId);
        LocalDate today = LocalDate.now(ZoneId.of(timezoneId));
        boolean checked = attendanceDayRepository.existsByUserIdAndAttendanceDate(uid, today);
        // 오늘 아직 안 했으면 어제까지의 연속 기록을 보여준다
        int streak = computeStreak(uid, checked ? today : today.minusDays(1));
        return new AttendanceStatusResponse(streak, checked, balanceOf(uid));
    }

    /** fromDay 부터 과거로 끊김 없이 이어진 출석 일수 (쿼리 1회) */
    int computeStreak(String userId, LocalDate fromDay) {
        List<LocalDate> dates = attendanceDayRepository.findDatesBetweenDesc(
                userId, fromDay.minusDays(STREAK_LOOKBACK_DAYS), fromDay);
        return countConsecutive(dates, fromDay);
    }

    static int countConsecutive(List<LocalDate> datesDesc, LocalDate fromDay) {
        int n = 0;
        LocalDate expected = fromDay;
        for (LocalDate d : datesDesc) {
            if (!d.equals(expected)) {
                break;
            }
            n++;
            expected = expected.minusDays(1);
        }
        return n;
    }

    private int balanceOf(String userId) {
        return accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }
}
