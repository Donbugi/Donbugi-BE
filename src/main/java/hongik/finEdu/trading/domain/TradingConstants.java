package hongik.finEdu.trading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TradingConstants {

    public static final BigDecimal INITIAL_CASH = new BigDecimal("10000000");
    public static final BigDecimal FEE_RATE = new BigDecimal("0.00015");
    public static final BigDecimal TAX_RATE = new BigDecimal("0.002");
    public static final int MARKET_OPEN_HOUR = 9;
    public static final int MARKET_OPEN_MINUTE = 0;
    public static final int MARKET_CLOSE_HOUR = 15;
    public static final int MARKET_CLOSE_MINUTE = 30;

    private static final List<FixedStock> STOCKS = List.of(
            new FixedStock("005930", "삼성전자"),
            new FixedStock("000660", "SK하이닉스"),
            new FixedStock("035420", "NAVER"),
            new FixedStock("005380", "현대차"),
            new FixedStock("051910", "LG화학"),
            new FixedStock("006400", "삼성SDI"),
            new FixedStock("035720", "카카오"),
            new FixedStock("207940", "삼성바이오로직스"),
            new FixedStock("068270", "셀트리온"),
            new FixedStock("000270", "기아"),
            new FixedStock("105560", "KB금융"),
            new FixedStock("055550", "신한지주"),
            new FixedStock("086790", "하나금융지주"),
            new FixedStock("316140", "우리금융지주"),
            new FixedStock("009150", "삼성전기"),
            new FixedStock("003670", "포스코퓨처엠"),
            new FixedStock("012330", "HD현대"),
            new FixedStock("028260", "삼성물산"),
            new FixedStock("033780", "KT&G"),
            new FixedStock("011200", "HMM")
    );

    private static final Map<String, FixedStock> BY_CODE = STOCKS.stream()
            .collect(Collectors.toUnmodifiableMap(FixedStock::code, s -> s));

    private TradingConstants() {}

    public static List<FixedStock> allStocks() {
        return STOCKS;
    }

    public static Optional<FixedStock> findStock(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim()));
    }

    public static FixedStock requireStock(String code) {
        return findStock(code).orElseThrow(() ->
                new hongik.finEdu.common.exception.BusinessException(
                        hongik.finEdu.common.exception.ErrorCode.INVALID_STOCK_CODE, code));
    }

    public static BigDecimal calcBuyFee(BigDecimal amount) {
        return amount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal calcSellFee(BigDecimal amount) {
        return amount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal calcSellTax(BigDecimal amount) {
        return amount.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);
    }
}
