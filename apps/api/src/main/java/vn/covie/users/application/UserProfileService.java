package vn.covie.users.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.api.dto.MeResponse;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.persistence.UserRepository;
import vn.covie.users.api.dto.UpdateMeRequest;
import vn.covie.media.application.MediaAttachmentPolicy;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;

@Service
public class UserProfileService {
    private final CurrentActorProvider actors; private final UserRepository users; private final UserIdentityRepository identities; private final AppClock clock;
    private final MediaAttachmentPolicy mediaPolicy;private final MediaAssetRepository media;private final MediaUrlResolver urls;
    public UserProfileService(CurrentActorProvider actors, UserRepository users, UserIdentityRepository identities, AppClock clock) {this(actors,users,identities,clock,null,null,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public UserProfileService(CurrentActorProvider actors,UserRepository users,UserIdentityRepository identities,AppClock clock,MediaAttachmentPolicy mediaPolicy,MediaAssetRepository media,MediaUrlResolver urls){this.actors=actors;this.users=users;this.identities=identities;this.clock=clock;this.mediaPolicy=mediaPolicy;this.media=media;this.urls=urls;}
    @Transactional(readOnly = true)
    public MeResponse me() { CurrentActor actor = actors.currentActor(); return response(user(actor.userId()), actor); }
    @Transactional
    public MeResponse update(UpdateMeRequest request) {
        String normalized = request.displayName()==null?"":request.displayName().trim();
        if(normalized.isBlank()||normalized.length()>120)throw invalid("Display name must contain 1 to 120 characters.");
        String phone=request.phone()==null?null:request.phone().trim();
        if(phone!=null&&phone.isBlank())phone=null;
        if(request.hasPhone()&&phone!=null){long digits=phone.chars().filter(Character::isDigit).count();if(phone.length()>30||!phone.matches("\\+?[0-9 ()-]+")||digits<7||digits>15)throw invalid("Phone number must contain 7 to 15 digits, with an optional country prefix.");}
        CurrentActor actor=actors.currentActor();UserEntity user=user(actor.userId());
        if(request.hasAvatarMediaId()&&request.avatarMediaId()!=null)mediaPolicy.requireReadyOwned(request.avatarMediaId(),actor.userId(),java.util.Set.of(MediaPurpose.USER_AVATAR));
        user.updateProfile(normalized,request.hasAvatarMediaId()?request.avatarMediaId():user.getAvatarMediaId(),clock.now());
        if(request.hasPhone())user.updatePhone(phone,clock.now());
        users.flush();return response(user,actor);
    }
    @Transactional
    public MeResponse update(String displayName) {
        return update(new UpdateMeRequest(displayName));
    }
    private UserEntity user(java.util.UUID userId) { return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found")); }
    private MeResponse response(UserEntity user, CurrentActor actor) {
        String email = identities.findAllByUserId(user.getId()).stream().filter(identity -> identity.getProvider() == IdentityProvider.EMAIL).map(identity -> identity.getEmailNormalized()).findFirst().orElse(null);
        List<String> roles = actor.globalRoles().stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList();
        String avatarUrl=user.getAvatarMediaId()==null||media==null?null:media.findById(user.getAvatarMediaId()).filter(asset->asset.getMediaStatus()==MediaStatus.READY).map(asset->urls.publicUrl(asset).toString()).orElse(null);
        return new MeResponse(user.getId(), user.getDisplayName(), email, user.getAccountStatus().name(), user.getVersion(), roles,user.getPhone(),user.getAvatarMediaId(),avatarUrl);
    }
    private ApiException invalid(String message){return new ApiException(ErrorCode.VALIDATION_FAILED,HttpStatus.BAD_REQUEST,message);}
}
