package hongik.finEdu.trading.domain;

import hongik.finEdu.trading.market.MarketType;

public record FixedStock(String code, String name, MarketType market) {

    public FixedStock(String code, String name) {
        this(code, name, MarketType.KOSPI);
    }
}
