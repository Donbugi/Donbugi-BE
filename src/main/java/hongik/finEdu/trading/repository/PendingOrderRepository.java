package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.entity.PendingOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingOrderRepository extends JpaRepository<PendingOrder, Long> {
    List<PendingOrder> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, OrderStatus status);

    List<PendingOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    Optional<PendingOrder> findByIdAndUserId(Long id, String userId);
}
