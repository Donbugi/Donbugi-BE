package hongik.finEdu.user.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 앱 사용자(외부 식별자 기준). 뉴스 관심 AI 한마디 등 부가 정보 보관.
 */
@Entity
@Table(
        name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_user_external_id", columnNames = "external_user_id")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API/클라이언트에서 쓰는 userId (예: 로그인 id, UUID) */
    @Column(name = "external_user_id", nullable = false, length = 64)
    private String externalUserId;

    /** 월간 관심 뉴스 토픽 기반 AI 한마디 */
    @Column(name = "news_insight_text", columnDefinition = "TEXT")
    private String newsInsightText;

    /** 인사이트가 귀속된 달 (Asia/Seoul, yyyy-MM) */
    @Column(name = "news_insight_year_month", length = 7)
    private String newsInsightYearMonth;

    /**
     * 당시 상위 토픽 목록 서명(SHA-256 hex).
     * 토픽 분포가 같으면 AI 재호출 없이 DB 문구 재사용.
     */
    @Column(name = "news_insight_signature", length = 64)
    private String newsInsightSignature;
}
