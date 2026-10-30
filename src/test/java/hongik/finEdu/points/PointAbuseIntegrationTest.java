package hongik.finEdu.points;

import hongik.finEdu.points.dto.PointRedeemRequest;
import hongik.finEdu.points.dto.RedeemLedgerResult;
import hongik.finEdu.points.dto.policy.AttendanceCheckInRequest;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.repository.PointHistoryRepository;
import hongik.finEdu.points.service.AttendancePointService;
import hongik.finEdu.points.service.PointLedgerService;
import hongik.finEdu.points.service.PointRewardService;
import hongik.finEdu.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 포인트 어뷰징 회귀 테스트 */
class PointAbuseIntegrationTest extends IntegrationTestSupport {

    @Autowired AttendancePointService attendancePointService;
    @Autowired PointRewardService pointRewardService;
    @Autowired PointLedgerService pointLedgerService;
    @Autowired PointAccountRepository accountRepository;
    @Autowired PointHistoryRepository historyRepository;

    private static String newUser() {
        return "pt-" + UUID.randomUUID();
    }

    @Test
    void 과거_날짜로_출석해도_오늘만_기록된다() {
        String user = newUser();
        var first = attendancePointService.checkIn(new AttendanceCheckInRequest(user, LocalDate.of(2026, 10, 1)));
        var second = attendancePointService.checkIn(new AttendanceCheckInRequest(user, LocalDate.of(2026, 10, 2)));
        assertThat(first.alreadyCheckedInForDate()).isFalse();
        assertThat(second.alreadyCheckedInForDate()).isTrue();
    }

    @Test
    void 같은_claim_동시_요청은_한_번만_지급된다() throws Exception {
        String user = newUser();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger awarded = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                PointAwardResult r = pointRewardService.tryAwardOnce(user, "test:same-claim:" + user, 20, "t", null);
                if (r.awarded()) {
                    awarded.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        assertThat(awarded.get()).isEqualTo(1);
        assertThat(accountRepository.findByUserId(user).orElseThrow().getBalance()).isEqualTo(20);
        assertThat(historyRepository.sumDelta(user)).isEqualTo(20);
    }

    @Test
    void 일일_획득_상한을_넘지_않는다() {
        String user = newUser();
        int total = 0;
        for (int i = 0; i < 40; i++) {
            total += pointRewardService.tryAwardOnce(user, "test:cap:" + user + ":" + i, 20, "t", null).amount();
        }
        assertThat(total).isEqualTo(500);
    }

    @Test
    void 같은_멱등키로_교환하면_한_번만_차감된다() {
        String user = newUser();
        String benefit = hongik.finEdu.points.domain.PointBenefitCode.CONVENIENCE_DISCOUNT.name();
        PointRedeemRequest req = new PointRedeemRequest(user, "it@example.com", benefit);

        // 첫 교환 시 데모 포인트(app.points.demo-initial-balance)와 함께 계정 생성
        RedeemLedgerResult a = pointLedgerService.applyRedemption(req, "redeem-key");
        int afterFirst = accountRepository.findByUserId(user).orElseThrow().getBalance();
        RedeemLedgerResult b = pointLedgerService.applyRedemption(req, "redeem-key");

        assertThat(a.replayed()).isFalse();
        assertThat(b.replayed()).isTrue();
        assertThat(b.redemptionRef()).isEqualTo(a.redemptionRef());
        assertThat(accountRepository.findByUserId(user).orElseThrow().getBalance()).isEqualTo(afterFirst);
        assertThat(historyRepository.sumDelta(user)).isEqualTo(afterFirst);
    }
}
