package hongik.finEdu.newscrawler.repository;

import hongik.finEdu.newscrawler.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrl(String url);

    /**
     * 본문이 있는 기사 중 무작위로 {@code limit}개 (PostgreSQL {@code random()})
     */
    @Query(value = """
            SELECT * FROM articles
            WHERE content IS NOT NULL AND trim(content) <> ''
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<Article> findRandomArticlesWithContent(@Param("limit") int limit);
    // 중복 체크 최적화 - URL 목록을 한 번에 조회: existsByUrl() N번 호출 대신 IN 쿼리 1번으로 해결
    @Query("SELECT a.url FROM Article a WHERE a.url IN :urls")
    Set<String> findExistingUrls(List<String> urls);
}
