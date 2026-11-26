package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Notification;
import com.grocery.repository.NotificationRepository;
import com.grocery.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final TenantContext tenantContext;

    public NotificationController(NotificationRepository notificationRepository, TenantContext tenantContext) {
        this.notificationRepository = notificationRepository;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(notificationRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(notificationRepository.countByStoreIdAndRead(storeId, false));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return notificationRepository.findByStoreIdAndId(storeId, id).map(notif -> {
            notif.setRead(true);
            Notification saved = notificationRepository.save(notif);
            return ResponseEntity.ok(ApiResponse.success("Marked as read", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        List<Notification> unread = notificationRepository.findByStoreIdAndReadOrderByCreatedAtDesc(storeId, false);
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return notificationRepository.findByStoreIdAndId(storeId, id).map(notif -> {
            notificationRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.<Void>success("Notification removed", null));
        }).orElse(ResponseEntity.notFound().build());
    }
}
