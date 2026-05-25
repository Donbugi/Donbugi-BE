package hongik.finEdu.main.notification.service;

import hongik.finEdu.calendar.dto.CalendarEventItemDto;
import hongik.finEdu.calendar.service.CalendarFeedService;
import hongik.finEdu.main.notification.dto.NotificationItemDto;
import hongik.finEdu.main.notification.dto.NotificationListResponse;
import hongik.finEdu.main.notification.entity.UserNotification;
import hongik.finEdu.main.notification.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository repository;
    private final CalendarFeedService calendarFeedService;

    @Transactional
    public NotificationListResponse listForUser(String userId) {
        seedTodayIfEmpty(userId);
        List<NotificationItemDto> items = repository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
        long unread = repository.countByUserIdAndReadAtIsNull(userId);
        return new NotificationListResponse(items, unread);
    }

    @Transactional
    public void markAllRead(String userId) {
        repository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(UserNotification::isUnread)
                .forEach(n -> {
                    n.markRead();
                    repository.save(n);
                });
    }

    private void seedTodayIfEmpty(String userId) {
        if (!repository.findTop20ByUserIdOrderByCreatedAtDesc(userId).isEmpty()) {
            return;
        }
        List<CalendarEventItemDto> today = calendarFeedService.getToday(userId);
        if (today.isEmpty()) {
            repository.save(UserNotification.builder()
                    .userId(userId)
                    .icon("⚔️")
                    .title("오늘의 과제 도착!")
                    .description("새로운 금융 퀴즈 3문제가 준비되었어요.")
                    .build());
            return;
        }
        for (CalendarEventItemDto event : today.stream().limit(3).toList()) {
            repository.save(UserNotification.builder()
                    .userId(userId)
                    .icon("🔔")
                    .title(event.title())
                    .description(event.description() != null ? event.description() : "오늘 예정된 금융 일정")
                    .build());
        }
        repository.save(UserNotification.builder()
                .userId(userId)
                .icon("⚔️")
                .title("오늘의 과제 도착!")
                .description("새로운 금융 퀴즈 3문제가 준비되었어요.")
                .build());
    }

    private NotificationItemDto toDto(UserNotification n) {
        return new NotificationItemDto(
                n.getId(),
                n.getIcon(),
                n.getTitle(),
                n.getDescription(),
                n.getCreatedAt(),
                n.isUnread()
        );
    }
}
