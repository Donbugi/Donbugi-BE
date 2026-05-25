package hongik.finEdu.main.notification.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationItemDto> items, long unreadCount) {
}
