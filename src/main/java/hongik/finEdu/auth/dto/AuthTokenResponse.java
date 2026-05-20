package hongik.finEdu.auth.dto;

/**
 * accessToken은 이후 {@code Authorization: Bearer} 및 타 API의 {@code userId}로 사용.
 */
public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        String userId,
        String email,
        String nickname
) {
    public static AuthTokenResponse of(String accessToken, String userId, String email, String nickname) {
        return new AuthTokenResponse(accessToken, "Bearer", userId, email, nickname);
    }
}
