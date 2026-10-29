package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

/**
 * 계좌 요약.
 * cash = 예수금(미체결 매수 묶인 금액 포함), availableCash = 주문가능금액.
 * totalProfit = 총자산 − 초기자금, realizedProfit = 매도 실현손익 누계, evaluationProfit = 보유종목 평가손익.
 */
public record AccountSummaryDto(
        String userId,
        BigDecimal cash,
        BigDecimal totalEvaluation,
        BigDecimal totalAsset,
        BigDecimal totalProfit,
        BigDecimal totalProfitRate,
        BigDecimal lockedCash,
        BigDecimal availableCash,
        BigDecimal realizedProfit,
        BigDecimal evaluationProfit
) {}
