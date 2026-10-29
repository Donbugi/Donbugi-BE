package hongik.finEdu.quiz.service;

import hongik.finEdu.quiz.entity.Quiz;
import hongik.finEdu.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 퀴즈 DB 저장 (write-behind).
 * 기존에는 QuizService 내부 메서드에 @Async 를 붙이고 같은 클래스에서 호출(self-invocation)해서
 * 프록시를 거치지 않아 실제로는 동기 실행됐다. 별도 빈으로 분리해 비동기가 적용되게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizPersistService {

    private final QuizRepository quizRepository;

    @Async
    public void saveAsync(Long articleId, String quizJson) {
        try {
            if (quizRepository.existsByArticleId(articleId)) {
                return;
            }
            quizRepository.save(Quiz.builder().articleId(articleId).quizJson(quizJson).build());
            log.info("[Write-Behind] DB 저장 완료: articleId={}", articleId);
        } catch (DataIntegrityViolationException e) {
            log.info("[Write-Behind] 이미 저장됨, 무시: articleId={}", articleId);
        } catch (Exception e) {
            log.error("[Write-Behind] DB 저장 실패: articleId={}, error={}", articleId, e.getMessage());
        }
    }
}
