package hongik.finEdu.main.notification.dto;

import java.time.LocalDateTime;

public record NotificationItemDto(
        Long id,
        String icon,
        String title,
        String description,
        LocalDateTime createdAt,
        boolean unread
) {
}
