package hongik.finEdu.quiz.repository;

import hongik.finEdu.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByArticleId(Long articleId);

    boolean existsByArticleId(Long articleId);

    @Query("SELECT MIN(q.id) FROM Quiz q")
    Long findMinId();

    @Query("SELECT MAX(q.id) FROM Quiz q")
    Long findMaxId();

    /**
     * 무작위 시작 id 부터 순환하며 limit 개 (인덱스 범위 스캔).
     * 기존 ORDER BY random() 은 테이블 전체를 읽고 정렬해 데이터가 늘수록 느려진다.
     */
    @Query(value = """
            (SELECT * FROM quiz WHERE id >= :start ORDER BY id LIMIT :limit)
            UNION ALL
            (SELECT * FROM quiz WHERE id < :start ORDER BY id LIMIT :limit)
            LIMIT :limit
            """, nativeQuery = true)
    List<Quiz> findFromRandomStart(@Param("start") long start, @Param("limit") int limit);
}
