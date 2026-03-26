package hongik.finEdu.newscrawler.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "articles",
        indexes = {
                @Index(name = "idx_category",     columnList = "category"),
                @Index(name = "idx_collected_at", columnList = "collectedAt"),
                @Index(name = "idx_summarized",   columnList = "isSummarized")
        }
)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long articleId;

    @Column(unique = true, nullable = false, length = 500)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String press;

    @Column(length = 100)
    private String journalist;

    @Column(length = 100)
    private String publishedAt;

    @Column(length = 50)
    private String category;

    private LocalDateTime collectedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Builder.Default
    private Boolean isSummarized = false;
}
