package hongik.finEdu.main.notification.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.main.notification.dto.NotificationListResponse;
import hongik.finEdu.main.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<NotificationListResponse> list(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(notificationService.listForUser(userId));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> readAll(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
