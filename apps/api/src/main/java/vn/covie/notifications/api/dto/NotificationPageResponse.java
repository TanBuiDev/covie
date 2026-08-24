package vn.covie.notifications.api.dto; import java.util.List; public record NotificationPageResponse(List<NotificationResponse> items,String nextCursor) {}
