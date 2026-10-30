package hongik.finEdu.news.interest.dto;

/**
 * 뉴스 읽음 기록.
 * articleId 가 있으면 서버가 기사 카테고리를 조회해 사용하고, (사용자, 날짜, 기사) 단위로 중복을 제거한다.
 * articleId 없이 category 만 보내는 기존 방식은 관심도 집계만 하고 읽기 보상은 지급하지 않는다.
 */
public record NewsInterestReadRequest(String userId, String category, Long articleId) {

    public NewsInterestReadRequest withUserId(String uid) {
        return new NewsInterestReadRequest(uid, category, articleId);
    }
}
