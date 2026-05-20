package hongik.finEdu.auth.dto;

/**
 * 닉네임 2자~10자(유니코드 기준 code point 개수), 공백은 앞뒤 trim.
 */
public record NicknameUpdateRequest(String nickname) {
}
