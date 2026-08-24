package vn.covie.moderation.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.covie.moderation.model.ModerationTargetType;

@Component
public class ModerationTargetHandlerRegistry {
    private final Map<ModerationTargetType, ModerationTargetHandler> handlers;
    public ModerationTargetHandlerRegistry(List<ModerationTargetHandler> available) {
        EnumMap<ModerationTargetType, ModerationTargetHandler> mapped = new EnumMap<>(ModerationTargetType.class);
        for (ModerationTargetHandler handler : available) {
            if (mapped.putIfAbsent(handler.supports(), handler) != null) throw new IllegalStateException("Multiple moderation target handlers for " + handler.supports());
        }
        handlers = Map.copyOf(mapped);
    }
    public ModerationTargetHandler require(ModerationTargetType type) {
        ModerationTargetHandler handler = handlers.get(type);
        if (handler == null) throw new IllegalStateException("No moderation target handler for " + type);
        return handler;
    }
}
