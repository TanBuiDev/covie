package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.catalog.application.CatalogPublishValidator;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public final class CatalogModerationTargetHandler implements ModerationTargetHandler {
    private final CatalogItemRepository items; private final CatalogPublishValidator validator;
    public CatalogModerationTargetHandler(CatalogItemRepository items,CatalogPublishValidator validator){this.items=items;this.validator=validator;}
    public ModerationTargetType supports(){return ModerationTargetType.CATALOG_ITEM;}
    public ModerationTargetSnapshot inspect(UUID id){return snapshot(items.findById(id).orElseThrow(CatalogModerationTargetHandler::notFound));}
    public ModerationTargetMutationResult apply(UUID id,ModerationActionType action,Instant now){CatalogItemEntity item=items.lockById(id).orElseThrow(CatalogModerationTargetHandler::notFound);var before=snapshot(item);try{switch(action){case APPROVE->{validator.requirePublishable(item);item.approveModeration(now);}case REJECT->item.rejectModeration(now);case HIDE->item.hideModeration(now);case RESTORE->item.restoreModeration(now);default->throw unsupported();}}catch(IllegalStateException e){throw state(e.getMessage());}items.saveAndFlush(item);return new ModerationTargetMutationResult(before,snapshot(item));}
    private static ModerationTargetSnapshot snapshot(CatalogItemEntity i){return new ModerationTargetSnapshot(ModerationTargetType.CATALOG_ITEM,i.getId(),i.getListingStatus().name(),i.getModerationStatus().name());}
    private static ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");} private static ApiException unsupported(){return new ApiException(ErrorCode.MODERATION_ACTION_NOT_SUPPORTED,HttpStatus.UNPROCESSABLE_ENTITY,"Moderation action is not supported for Catalog.");} private static ApiException state(String m){return new ApiException(ErrorCode.MODERATION_TARGET_STATE_CONFLICT,HttpStatus.CONFLICT,m);}
}
