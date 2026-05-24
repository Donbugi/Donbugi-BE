package hongik.finEdu.trading.service;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.entity.PendingOrder;
import hongik.finEdu.trading.repository.PendingOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOrderCheckService {

    private final PendingOrderRepository pendingOrderRepository;
    private final StockPriceService stockPriceService;
    private final TradingExecutionService executionService;

    @Transactional
    public void checkAndFillPendingOrders() {
        List<PendingOrder> pending = pendingOrderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.PENDING);
        int filled = 0;
        for (PendingOrder order : pending) {
            try {
                BigDecimal current = stockPriceService.getQuote(order.getStockCode()).currentPrice();
                if (shouldFill(order, current)) {
                    executionService.executeLimitOrder(order, current);
                    filled++;
                }
            } catch (Exception e) {
                log.warn("[모의투자] 지정가 체결 실패 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
        if (filled > 0) {
            log.info("[모의투자] 지정가 체결 {}건", filled);
        }
    }

    @Transactional
    public void cancelAllPendingOrdersAtMarketClose() {
        List<PendingOrder> pending = pendingOrderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.PENDING);
        for (PendingOrder order : pending) {
            try {
                executionService.cancelPendingOrder(order);
            } catch (Exception e) {
                log.warn("[모의투자] 장마감 주문취소 실패 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
        if (!pending.isEmpty()) {
            log.info("[모의투자] 장마감 미체결 {}건 취소", pending.size());
        }
    }

    private static boolean shouldFill(PendingOrder order, BigDecimal current) {
        if (order.getOrderType() == OrderType.BUY) {
            return current.compareTo(order.getTargetPrice()) <= 0;
        }
        return current.compareTo(order.getTargetPrice()) >= 0;
    }
}
