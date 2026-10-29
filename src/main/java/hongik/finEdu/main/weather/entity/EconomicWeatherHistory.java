package hongik.finEdu.main.weather.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일별 경제 날씨 기록 (추이 차트·"어제 대비" 비교용). 하루 1행, 갱신 시 덮어씀. */
@Entity
@Table(
        name = "economic_weather_history",
        uniqueConstraints = @UniqueConstraint(name = "uk_weather_history_date", columnNames = "weather_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EconomicWeatherHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weather_date", nullable = false)
    private LocalDate weatherDate;

    @Column(name = "weather_code", nullable = false, length = 20)
    private String weatherCode;

    @Column(nullable = false)
    private int score;

    @Column(name = "kospi_change_rate", precision = 9, scale = 4)
    private BigDecimal kospiChangeRate;

    @Column(name = "sp500_change_rate", precision = 9, scale = 4)
    private BigDecimal sp500ChangeRate;

    @Column(name = "fx_ten_day_change_rate", precision = 9, scale = 4)
    private BigDecimal fxTenDayChangeRate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public EconomicWeatherHistory(LocalDate weatherDate) {
        this.weatherDate = weatherDate;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String weatherCode, int score, BigDecimal kospi, BigDecimal sp500, BigDecimal fx) {
        this.weatherCode = weatherCode;
        this.score = score;
        this.kospiChangeRate = kospi;
        this.sp500ChangeRate = sp500;
        this.fxTenDayChangeRate = fx;
        this.updatedAt = LocalDateTime.now();
    }
}
