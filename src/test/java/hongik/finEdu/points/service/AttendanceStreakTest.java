package hongik.finEdu.points.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceStreakTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 29);

    @Test
    void 연속된_날짜만_센다() {
        List<LocalDate> desc = List.of(D, D.minusDays(1), D.minusDays(2), D.minusDays(4));
        assertThat(AttendancePointService.countConsecutive(desc, D)).isEqualTo(3);
    }

    @Test
    void 기준일에_출석이_없으면_0() {
        assertThat(AttendancePointService.countConsecutive(List.of(D.minusDays(1)), D)).isZero();
    }

    @Test
    void 빈_목록은_0() {
        assertThat(AttendancePointService.countConsecutive(List.of(), D)).isZero();
    }
}
