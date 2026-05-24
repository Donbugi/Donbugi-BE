package hongik.finEdu.trading.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.dto.MarketOrderResponseDto;
import hongik.finEdu.trading.entity.PendingOrder;
import hongik.finEdu.trading.entity.TradeHistory;
import hongik.finEdu.trading.entity.TradingAccount;
import hongik.finEdu.trading.entity.TradingHolding;
import hongik.finEdu.trading.repository.PendingOrderRepository;
import hongik.finEdu.trading.repository.TradeHistoryRepository;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TradingExecutionService {

    private final TradingAccountRepository accountRepository;
    private final TradingHoldingRepository holdingRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final PendingOrderRepository pendingOrderRepository;

    @Transactional
    public MarketOrderResponseDto executeMarketBuy(String userId, String stockCode, int quantity, BigDecimal price) {
        validateQuantity(quantity);
        TradingConstants.requireStock(stockCode);

        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = TradingConstants.calcBuyFee(amount);
        BigDecimal totalCost = amount.add(fee);

        TradingAccount account = getOrCreateAccount(userId);
        if (account.getCash().compareTo(totalCost) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                    "필요 " + totalCost + "원, 보유 " + account.getCash() + "원");
        }

        account.debit(totalCost);
        accountRepository.save(account);

        upsertHoldingBuy(userId, stockCode, quantity, price);

        TradeHistory history = tradeHistoryRepository.save(TradeHistory.builder()
                .userId(userId)
                .stockCode(stockCode)
                .orderType(OrderType.BUY)
                .priceType(PriceType.MARKET)
                .quantity(quantity)
                .price(price)
                .fee(fee)
                .tax(BigDecimal.ZERO)
                .totalAmount(totalCost)
                .build());

        return new MarketOrderResponseDto(
                history.getId(), stockCode, OrderType.BUY, quantity, price, fee, BigDecimal.ZERO, totalCost);
    }

    @Transactional
    public MarketOrderResponseDto executeMarketSell(String userId, String stockCode, int quantity, BigDecimal price) {
        validateQuantity(quantity);
        TradingConstants.requireStock(stockCode);

        TradingHolding holding = holdingRepository.findByUserIdAndStockCode(userId, stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY));
        if (holding.getQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY,
                    "보유 " + holding.getQuantity() + "주, 요청 " + quantity + "주");
        }

        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = TradingConstants.calcSellFee(amount);
        BigDecimal tax = TradingConstants.calcSellTax(amount);
        BigDecimal netProceeds = amount.subtract(fee).subtract(tax);

        holding.reduceQuantity(quantity);
        if (holding.getQuantity() == 0) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }

        TradingAccount account = getOrCreateAccount(userId);
        account.credit(netProceeds);
        accountRepository.save(account);

        TradeHistory history = tradeHistoryRepository.save(TradeHistory.builder()
                .userId(userId)
                .stockCode(stockCode)
                .orderType(OrderType.SELL)
                .priceType(PriceType.MARKET)
                .quantity(quantity)
                .price(price)
                .fee(fee)
                .tax(tax)
                .totalAmount(netProceeds)
                .build());

        return new MarketOrderResponseDto(
                history.getId(), stockCode, OrderType.SELL, quantity, price, fee, tax, netProceeds);
    }

    /** 지정가 체결 — pending order 기준 */
    @Transactional
    public void executeLimitOrder(PendingOrder order, BigDecimal currentPrice) {
        if (order.getOrderType() == OrderType.BUY) {
            executeLimitBuyFill(order, currentPrice);
        } else {
            executeLimitSellFill(order, currentPrice);
        }
        order.markDone();
        pendingOrderRepository.save(order);
    }

    @Transactional
    public void cancelPendingOrder(PendingOrder order) {
        if (order.getStatus() != hongik.finEdu.trading.domain.OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_DONE);
        }

        if (order.getOrderType() == OrderType.BUY) {
            TradingAccount account = getOrCreateAccount(order.getUserId());
            if (order.getReservedAmount() != null) {
                account.credit(order.getReservedAmount());
                accountRepository.save(account);
            }
        } else {
            var existing = holdingRepository.findByUserIdAndStockCode(order.getUserId(), order.getStockCode());
            if (existing.isPresent()) {
                existing.get().restoreQuantity(order.getQuantity());
                holdingRepository.save(existing.get());
            } else if (order.getSellAvgPrice() != null) {
                holdingRepository.save(TradingHolding.builder()
                        .userId(order.getUserId())
                        .stockCode(order.getStockCode())
                        .quantity(order.getQuantity())
                        .avgPrice(order.getSellAvgPrice())
                        .build());
            } else {
                throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY, "매도 주문 복원 실패");
            }
        }

        order.markCancelled();
        pendingOrderRepository.save(order);
    }

    @Transactional
    public TradingAccount getOrCreateAccount(String userId) {
        return accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(TradingAccount.builder()
                        .userId(userId)
                        .cash(TradingConstants.INITIAL_CASH)
                        .build()));
    }

    private void executeLimitBuyFill(PendingOrder order, BigDecimal fillPrice) {
        int quantity = order.getQuantity();
        BigDecimal amount = fillPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = TradingConstants.calcBuyFee(amount);
        BigDecimal actualCost = amount.add(fee);

        TradingAccount account = getOrCreateAccount(order.getUserId());
        BigDecimal reserved = order.getReservedAmount() != null ? order.getReservedAmount() : BigDecimal.ZERO;
        BigDecimal refund = reserved.subtract(actualCost);
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            account.credit(refund);
        } else if (refund.compareTo(BigDecimal.ZERO) < 0) {
            if (account.getCash().compareTo(refund.abs()) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CASH);
            }
            account.debit(refund.abs());
        }
        accountRepository.save(account);

        upsertHoldingBuy(order.getUserId(), order.getStockCode(), quantity, fillPrice);

        tradeHistoryRepository.save(TradeHistory.builder()
                .userId(order.getUserId())
                .stockCode(order.getStockCode())
                .orderType(OrderType.BUY)
                .priceType(PriceType.LIMIT)
                .quantity(quantity)
                .price(fillPrice)
                .fee(fee)
                .tax(BigDecimal.ZERO)
                .totalAmount(actualCost)
                .build());
    }

    private void executeLimitSellFill(PendingOrder order, BigDecimal fillPrice) {
        int quantity = order.getQuantity();
        BigDecimal amount = fillPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = TradingConstants.calcSellFee(amount);
        BigDecimal tax = TradingConstants.calcSellTax(amount);
        BigDecimal netProceeds = amount.subtract(fee).subtract(tax);

        TradingAccount account = getOrCreateAccount(order.getUserId());
        account.credit(netProceeds);
        accountRepository.save(account);

        tradeHistoryRepository.save(TradeHistory.builder()
                .userId(order.getUserId())
                .stockCode(order.getStockCode())
                .orderType(OrderType.SELL)
                .priceType(PriceType.LIMIT)
                .quantity(quantity)
                .price(fillPrice)
                .fee(fee)
                .tax(tax)
                .totalAmount(netProceeds)
                .build());
    }

    private void upsertHoldingBuy(String userId, String stockCode, int quantity, BigDecimal price) {
        var existing = holdingRepository.findByUserIdAndStockCode(userId, stockCode);
        if (existing.isPresent()) {
            TradingHolding h = existing.get();
            h.addQuantity(quantity, price);
            holdingRepository.save(h);
        } else {
            holdingRepository.save(TradingHolding.builder()
                    .userId(userId)
                    .stockCode(stockCode)
                    .quantity(quantity)
                    .avgPrice(price.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1 이상이어야 합니다.");
        }
    }
}
