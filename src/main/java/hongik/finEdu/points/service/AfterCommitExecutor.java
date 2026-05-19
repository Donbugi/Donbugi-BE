package hongik.finEdu.points.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class AfterCommitExecutor {

    public void runAfterCommit(String label, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeRun(label, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeRun(label, task);
            }
        });
    }

    private void safeRun(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[afterCommit] {} 단계 실패", label, e);
        }
    }
}
