package hongik.finEdu.main.notification.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.main.notification.dto.NotificationListResponse;
import hongik.finEdu.main.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = OpenApiTags.NOTIFICATION)
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "알림 목록",
            description = """
                    **푸시/이메일 아님** — DB 저장형 앱 내 알림.
                    
                    **최초 호출 시 자동 생성(seed):**
                    - 해당 user 알림 0건이면 생성
                    - 오늘 금융 일정 최대 3건 (🔔)
                    - '오늘의 과제 도착!' 1건 (⚔️)
                    - 일정 없으면 과제 알림만 1건
                    
                    **이후:** 기존 알림 반환 (새 이벤트 자동 추가 없음)
                    
                    unreadCount: read_at 이 null 인 건수""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<NotificationListResponse> list(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(notificationService.listForUser(userId));
    }

    @Operation(summary = "전체 읽음 처리", description = "최근 20건 중 미읽음 알림의 read_at 설정")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> readAll(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
