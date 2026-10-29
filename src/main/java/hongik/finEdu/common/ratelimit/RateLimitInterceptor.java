package hongik.finEdu.common.ratelimit;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.List;

/**
 * 보상·주문·AI 호출 API 에 사용자(비로그인은 IP)별 분당 한도를 건다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    record Rule(String name, String method, String pattern, int perMinute) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("trading-order", "POST", "/api/orders/**", 30),
            new Rule("trading-order-del", "DELETE", "/api/orders/**", 30),
            new Rule("trading-order-patch", "PATCH", "/api/orders/**", 30),
            new Rule("quiz-answer", "POST", "/api/quiz/**", 30),
            new Rule("quiz-ai", "GET", "/api/quiz/**", 60),
            new Rule("points", "POST", "/api/points/**", 20),
            new Rule("news-read", "POST", "/api/news/interest/read", 60)
    );

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final RedisRateLimiter limiter;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        for (Rule rule : RULES) {
            if (rule.method().equalsIgnoreCase(method) && matcher.match(rule.pattern(), path)) {
                if (!limiter.tryAcquire(rule.name(), subject(request), rule.perMinute(), Duration.ofMinutes(1))) {
                    response.setHeader("Retry-After", "60");
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
                }
                return true;
            }
        }
        return true;
    }

    private static String subject(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            return "u:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
