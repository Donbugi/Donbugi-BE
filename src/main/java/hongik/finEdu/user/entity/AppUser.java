package hongik.finEdu.user.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 앱 사용자. {@code externalUserId}는 API·포인트 등에서 쓰는 공개 userId(회원가입 시 UUID).
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

    /** 클라이언트/타 API에 전달하는 사용자 식별자 (UUID 문자열) */
    @Column(name = "external_user_id", nullable = false, length = 64)
    private String externalUserId;

    /** 이메일 가입 시 설정. 레거시 행은 null 가능 */
    @Column(length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", length = 120)
    private String passwordHash;

    /** 가입 직후 null, 로그인 후 2~10자(유니코드)로 설정 */
    @Column(length = 40)
    private String nickname;

    @Column(name = "news_insight_text", columnDefinition = "TEXT")
    private String newsInsightText;

    @Column(name = "news_insight_year_month", length = 7)
    private String newsInsightYearMonth;

    @Column(name = "news_insight_signature", length = 64)
    private String newsInsightSignature;
}
