package hongik.finEdu.auth.controller;

import hongik.finEdu.auth.dto.AuthTokenResponse;
import hongik.finEdu.auth.dto.LoginRequest;
import hongik.finEdu.auth.dto.MeProfileResponse;
import hongik.finEdu.auth.dto.NicknameUpdateRequest;
import hongik.finEdu.auth.dto.SignupRequest;
import hongik.finEdu.auth.service.AuthService;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = OpenApiTags.AUTH, description = "회원가입·로그인·프로필")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "회원가입",
            description = """
                    이메일·비밀번호(8~72자)로 가입합니다.
                    - 성공 시 JWT accessToken과 userId(UUID)를 반환합니다.
                    - 닉네임·온보딩은 가입 후 별도 API로 진행합니다.""")
    @ApiResponse(responseCode = "201", description = "가입 성공 + JWT 발급")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일 (U001)")
    @PostMapping("/signup")
    public ResponseEntity<AuthTokenResponse> signup(@RequestBody SignupRequest request) {
        AuthTokenResponse body = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(
            summary = "로그인",
            description = "이메일·비밀번호로 로그인하고 JWT accessToken을 받습니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치 (U002)")
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "내 프로필 조회",
            description = """
                    JWT 사용자의 프로필을 반환합니다.
                    - nickname, onboardingCompleted, characterLevel/emoji/name
                    - finIqBalance, finIqLevel, pointsToNextLevel, finIqProgressPercent 포함""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/me")
    public ResponseEntity<MeProfileResponse> me(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    @Operation(
            summary = "닉네임 설정·변경",
            description = "닉네임 2~10자(유니코드 기준). Bearer JWT 필수.")
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
