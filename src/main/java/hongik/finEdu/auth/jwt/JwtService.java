package hongik.finEdu.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMillis;

    /**
     * 시크릿은 반드시 외부(APP_AUTH_JWT_SECRET)에서 주입한다. 레포에 기본값을 두지 않는다.
     * local/test 프로필에서만, 미설정 시 기동마다 바뀌는 임시 랜덤 키를 사용한다(재시작하면 기존 토큰 무효).
     */
    public JwtService(
            @Value("${app.auth.jwt.secret:}") String secret,
            @Value("${app.auth.jwt.expiration-hours:720}") long expirationHours,
            Environment environment
    ) {
        byte[] bytes;
        if ((secret == null || secret.isBlank()) && environment.acceptsProfiles(Profiles.of("local", "test"))) {
            bytes = new byte[64];
            new SecureRandom().nextBytes(bytes);
            log.warn("[jwt] APP_AUTH_JWT_SECRET 미설정 — local/test 전용 임시 키를 사용합니다.");
        } else {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("APP_AUTH_JWT_SECRET(app.auth.jwt.secret) 환경변수가 필요합니다.");
            }
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("app.auth.jwt.secret 은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMillis = Duration.ofHours(expirationHours).toMillis();
    }

    public String createAccessToken(String subjectUserId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectUserId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(key)
                .compact();
    }

    public String parseSubject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
