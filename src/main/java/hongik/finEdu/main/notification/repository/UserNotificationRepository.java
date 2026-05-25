package hongik.finEdu.main.notification.repository;

import hongik.finEdu.main.notification.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndReadAtIsNull(String userId);
}
