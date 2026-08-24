package vn.covie.realtime; import java.util.Map; import java.util.UUID; public record SseSignal(UUID userId,String eventName,Map<String,Object> data) {}
