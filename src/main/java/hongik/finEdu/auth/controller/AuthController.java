package hongik.finEdu.auth.controller;

import hongik.finEdu.auth.dto.AuthTokenResponse;
import hongik.finEdu.auth.dto.LoginRequest;
import hongik.finEdu.auth.dto.MeProfileResponse;
import hongik.finEdu.auth.dto.NicknameUpdateRequest;
import hongik.finEdu.auth.dto.SignupRequest;
import hongik.finEdu.auth.service.AuthService;
import hongik.finEdu.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 회원가입 — 이메일·비밀번호만. 응답에 JWT 포함(바로 로그인된 것과 동일). */
    @PostMapping("/signup")
    public ResponseEntity<AuthTokenResponse> signup(@RequestBody SignupRequest request) {
        AuthTokenResponse body = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** 현재 로그인 사용자 프로필. 헤더 Authorization: Bearer accessToken */
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/me")
    public ResponseEntity<MeProfileResponse> me(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    /** 닉네임 설정·변경 (2~10자). Bearer 필수. */
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PatchMapping("/me")
    public ResponseEntity<MeProfileResponse> patchMe(
            Authentication authentication,
            @RequestBody NicknameUpdateRequest request
    ) {
        String userId = authentication.getName();
        return ResponseEntity.ok(authService.updateNickname(userId, request));
    }
}
