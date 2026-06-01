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

    /** random-session: AI 호출 없이 DB에 저장된 퀴즈만 무작위 조회 */
    @Query(value = "SELECT * FROM quiz ORDER BY random() LIMIT :limit", nativeQuery = true)
    List<Quiz> findRandomStored(@Param("limit") int limit);
}
