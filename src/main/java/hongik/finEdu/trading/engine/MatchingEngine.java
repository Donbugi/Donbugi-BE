package hongik.finEdu.trading.engine;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.PriceType;
import hongik.finEdu.trading.client.StockQuote;
import hongik.finEdu.trading.entity.TradingOrder;
import hongik.finEdu.trading.market.Fill;
import hongik.finEdu.trading.market.OrderBook;
import hongik.finEdu.trading.repository.TradingOrderRepository;
import hongik.finEdu.trading.service.OrderBookService;
import hongik.finEdu.trading.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 체결 엔진 (스케줄러에서 호출, 분산 락으로 단일 인스턴스 실행).
 *
 * <ul>
 *   <li>접속매매: 종목별로 호가를 1번만 조회 → 시간 우선으로 주문을 처리하면서 체결분만큼 호가 잔량 차감</li>
 *   <li>동시호가: 시가(09:00)/종가(15:30) 단일가로 일괄 체결</li>
 *   <li>장 마감: 미체결 잔량 일괄 취소 (lock 해제)</li>
 * </ul>
 * 주문 1건 처리 실패가 다른 주문에 영향을 주지 않도록 각 처리는 OrderExecutionService 의 REQUIRES_NEW 트랜잭션.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngine {

    static final Set<OrderStatus> OPEN = EnumSet.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository orderRepository;
    private final OrderExecutionService executionService;
    private final OrderBookService orderBookService;
    private final StockPriceService stockPriceService;

    /** 접속매매 중 미체결 지정가 주문 매칭 */
    public int matchContinuous() {
        int fills = 0;
        for (String stockCode : orderRepository.findStocksWithOpenOrders(OPEN)) {
            try {
                fills += matchStock(stockCode);
            } catch (Exception e) {
                log.warn("[엔진] {} 매칭 실패: {}", stockCode, e.getMessage());
            }
        }
        if (fills > 0) {
            log.info("[엔진] 지정가 체결 {}건", fills);
        }
        return fills;
    }

    private int matchStock(String stockCode) {
        List<TradingOrder> orders = orderRepository.findOpenByStock(stockCode, OPEN);
        if (orders.isEmpty()) {
            return 0;
        }
        OrderBook book = orderBookService.fetchFresh(stockCode);
        StockPriceService.PriceRange range = stockPriceService.drainObservedRange(stockCode);
        int fills = 0;
        for (TradingOrder o : orders) {
            if (o.getPriceType() != PriceType.LIMIT) {
                continue; // 동시호가 시장가 잔량은 단일가 체결에서 처리
            }
            try {
                Fill fill = executionService.matchLimitOrder(o.getId(), book, range);
                if (!fill.isEmpty()) {
                    book = book.consume(fill, o.getOrderType() == hongik.finEdu.trading.domain.OrderType.BUY);
                    fills++;
                }
            } catch (Exception e) {
                log.warn("[엔진] orderId={} 체결 실패: {}", o.getId(), e.getMessage());
            }
        }
        return fills;
    }

    /**
     * 단일가 일괄 체결.
     * @param useOpenPrice true=장 시작(시가), false=장 마감(현재가=종가)
     */
    public int runAuction(boolean useOpenPrice) {
        int processed = 0;
        for (String stockCode : orderRepository.findStocksWithOpenOrders(OPEN)) {
            try {
                StockQuote q = stockPriceService.refresh(stockCode).quote();
                BigDecimal price = useOpenPrice && q.openPrice() != null && q.openPrice().signum() > 0
                        ? q.openPrice() : q.currentPrice();
                for (TradingOrder o : orderRepository.findOpenByStock(stockCode, OPEN)) {
                    try {
                        executionService.fillAtAuction(o.getId(), price);
                        processed++;
                    } catch (Exception e) {
                        log.warn("[엔진] 단일가 체결 실패 orderId={}: {}", o.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("[엔진] {} 단일가 시세 조회 실패: {}", stockCode, e.getMessage());
            }
        }
        log.info("[엔진] 단일가({}) 처리 {}건", useOpenPrice ? "시가" : "종가", processed);
        return processed;
    }

    /** 장 마감 후 미체결 잔량 취소 */
    public int expireAll() {
        int n = 0;
        for (Long id : orderRepository.findOpenIds(OPEN)) {
            try {
                executionService.expire(id);
                n++;
            } catch (Exception e) {
                log.warn("[엔진] 만료 실패 orderId={}: {}", id, e.getMessage());
            }
        }
        if (n > 0) {
            log.info("[엔진] 장 마감 미체결 {}건 취소", n);
        }
        return n;
    }
}
