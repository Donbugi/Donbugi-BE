package hongik.finEdu.quiz.service;

import hongik.finEdu.newscrawler.service.ArticlesSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 크롤링 직후 퀴즈 사전 생성.
 * 사용자가 기사 상세에 처음 들어갈 때 AI 호출(수 초~수십 초)을 기다리던 구조를 제거한다.
 * 비동기 + 배치당 최대 N건 + 순차 처리로 AI 서버 부하를 제한한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizPregenerator {

    private final QuizService quizService;

    @Value("${quiz.pregenerate.enabled:true}")
    private boolean enabled;

    @Value("${quiz.pregenerate.max-per-batch:30}")
    private int maxPerBatch;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onArticlesSaved(ArticlesSavedEvent event) {
        if (!enabled) {
            return;
        }
        int ok = 0;
        int fail = 0;
        for (Long id : event.articleIds().stream().limit(maxPerBatch).toList()) {
            try {
                if (!quizService.hasQuiz(id)) {
                    quizService.getQuiz(id);
                    ok++;
                }
            } catch (Exception e) {
                fail++;
                log.warn("[퀴즈 사전생성] articleId={} 실패: {}", id, e.getMessage());
            }
        }
        log.info("[퀴즈 사전생성] 성공 {}건, 실패 {}건 (요청 {}건)", ok, fail, event.articleIds().size());
    }
}
