package hongik.finEdu.auth.support;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthUserResolver {

    public String requireUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        String userId = authentication.getName();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId.trim();
    }

    /** Bearer 토큰 userId와 요청 userId가 일치하는지 검증 (body/query 공통) */
    public String requireMatchingUserId(Authentication authentication, String requestedUserId) {
        String tokenUserId = requireUserId(authentication);
        if (requestedUserId == null || requestedUserId.isBlank()) {
            return tokenUserId;
        }
        String req = requestedUserId.trim();
        if (!tokenUserId.equals(req)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "다른 사용자의 데이터에 접근할 수 없습니다.");
        }
        return tokenUserId;
    }
}
