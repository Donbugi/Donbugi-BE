package hongik.finEdu.auth.service;

import hongik.finEdu.auth.dto.AuthTokenResponse;
import hongik.finEdu.auth.dto.LoginRequest;
import hongik.finEdu.auth.dto.MeProfileResponse;
import hongik.finEdu.auth.dto.NicknameUpdateRequest;
import hongik.finEdu.auth.dto.SignupRequest;
import hongik.finEdu.auth.jwt.JwtService;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.user.entity.AppUser;
import hongik.finEdu.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 72;
    private static final int NICKNAME_MIN_CP = 2;
    private static final int NICKNAME_MAX_CP = 10;
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthTokenResponse signup(SignupRequest request) {
        String email = normalizeEmail(requireText(request.email(), "email"));
        String password = requirePassword(request.password());
        validateEmailShape(email);
        validatePasswordLength(password);
        if (appUserRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        AppUser user = AppUser.builder()
                .externalUserId(UUID.randomUUID().toString())
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .nickname(null)
                .build();
        appUserRepository.save(user);
        String token = jwtService.createAccessToken(user.getExternalUserId());
        return AuthTokenResponse.of(token, user.getExternalUserId(), user.getEmail(), user.getNickname());
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        String email = normalizeEmail(requireText(request.email(), "email"));
        String password = requirePassword(request.password());
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        String token = jwtService.createAccessToken(user.getExternalUserId());
        return AuthTokenResponse.of(token, user.getExternalUserId(), user.getEmail(), user.getNickname());
    }

    @Transactional(readOnly = true)
    public MeProfileResponse getProfile(String externalUserId) {
        AppUser user = appUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "사용자를 찾을 수 없습니다."));
        return new MeProfileResponse(user.getExternalUserId(), user.getEmail(), user.getNickname());
    }

    @Transactional
    public MeProfileResponse updateNickname(String externalUserId, NicknameUpdateRequest request) {
        String raw = requireText(request.nickname(), "nickname");
        String nickname = validateAndTrimNickname(raw);
        AppUser user = appUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "사용자를 찾을 수 없습니다."));
        user.setNickname(nickname);
        appUserRepository.save(user);
        return new MeProfileResponse(user.getExternalUserId(), user.getEmail(), user.getNickname());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, field + "는 필수입니다.");
        }
        return value.trim();
    }

    /** 비밀번호는 내부 공백을 유지하고, 전부 공백인 값만 거절 */
    private static String requirePassword(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "password는 필수입니다.");
        }
        if (value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "password는 필수입니다.");
        }
        return value;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void validateEmailShape(String email) {
        if (!EMAIL.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다.");
        }
    }

    private void validatePasswordLength(String password) {
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비밀번호는 " + PASSWORD_MIN + "~" + PASSWORD_MAX + "자여야 합니다.");
        }
    }

    private static String validateAndTrimNickname(String nickname) {
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 비어 있을 수 없습니다.");
        }
        int cps = trimmed.codePointCount(0, trimmed.length());
        if (cps < NICKNAME_MIN_CP || cps > NICKNAME_MAX_CP) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "닉네임은 " + NICKNAME_MIN_CP + "자 이상 " + NICKNAME_MAX_CP + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}
