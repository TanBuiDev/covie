package vn.covie.notifications.application;
import java.util.UUID;
public record NotificationCreationResult(UUID notificationId, boolean created) {}
