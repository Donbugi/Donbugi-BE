package hongik.finEdu.auth.dto;

/**
 * 회원가입 — 이메일·비밀번호만.
 */
public record SignupRequest(String email, String password) {
}
