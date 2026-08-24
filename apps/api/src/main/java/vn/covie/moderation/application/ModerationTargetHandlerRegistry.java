package vn.covie.moderation.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class ModerationTargetHandlerRegistry {
    private final Map<ModerationTargetType,ModerationTargetHandler> handlers;
    public ModerationTargetHandlerRegistry(List<ModerationTargetHandler> discovered){
        EnumMap<ModerationTargetType,ModerationTargetHandler> map=new EnumMap<>(ModerationTargetType.class);
        for(var h:discovered) if(map.putIfAbsent(h.supports(),h)!=null) throw new IllegalStateException("Duplicate moderation handler for "+h.supports());
        for(var required:List.of(ModerationTargetType.CATALOG_ITEM,ModerationTargetType.NEED_REQUEST,ModerationTargetType.MERCHANT)) if(!map.containsKey(required)) throw new IllegalStateException("Missing moderation handler for "+required);
        handlers=Map.copyOf(map);
    }
    public ModerationTargetHandler require(ModerationTargetType type){
        ModerationTargetHandler handler=handlers.get(type);
        if(handler==null) throw new ApiException(ErrorCode.MODERATION_ACTION_NOT_SUPPORTED,HttpStatus.UNPROCESSABLE_ENTITY,"Moderation target handler is unavailable for "+type+".");
        return handler;
    }
}
