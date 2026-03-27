package hongik.finEdu.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz", uniqueConstraints = @UniqueConstraint(columnNames = "article_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "quiz_json", columnDefinition = "TEXT", nullable = false)
    private String quizJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Quiz(Long articleId, String quizJson) {
        this.articleId = articleId;
        this.quizJson = quizJson;
        this.createdAt = LocalDateTime.now();
    }
}
