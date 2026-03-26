package hongik.finEdu.calendar.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(
    name = "tax_schedule",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"schedule_date", "title"})
    },
    indexes = {
        @Index(name = "idx_schedule_date", columnList = "schedule_date"),
        @Index(name = "idx_year_month", columnList = "year, month")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class TaxSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;       // 실제 일정 날짜 (year + month + day 조합)

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int day;

    @Column(nullable = false, length = 500)
    private String title;                 // 일정 항목명 (bbs_tit)

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;            // 크롤링 출처 URL

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;     // 크롤링 시각

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;     // 마지막 업데이트 시각

    @Builder
    public TaxSchedule(int year, int month, int day,
                       String title, String sourceUrl) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.scheduleDate = LocalDate.of(year, month, day);
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.crawledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 재크롤링 시 내용 업데이트
    public void update(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }
}
