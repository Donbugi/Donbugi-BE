package hongik.finEdu.calendar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "issue_schedule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_date", "seq_no"}),
        indexes = {
                @Index(name = "idx_issue_schedule_date", columnList = "schedule_date"),
                @Index(name = "idx_issue_year_month", columnList = "year, month")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class IssueSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    private int year;
    private int month;
    private int day;

    /** 삼성증권 일간이슈 분류(표시명) */
    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 500)
    private String title;

    /** API 원본 분류코드 (001~015 등) */
    @Column(name = "group_code", length = 8)
    private String groupCode;

    @Column(name = "seq_no", nullable = false, length = 32)
    private String seqNo;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public IssueSchedule(int year, int month, int day, String category, String title,
                         String groupCode, String seqNo, String sourceUrl) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.scheduleDate = LocalDate.of(year, month, day);
        this.category = category;
        this.title = title;
        this.groupCode = groupCode;
        this.seqNo = seqNo;
        this.sourceUrl = sourceUrl;
        this.crawledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
