package vn.covie.users.application;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.support.PostgresIntegrationTest;import vn.covie.auth.security.CurrentActorProvider;import vn.covie.shared.security.*;import vn.covie.users.persistence.*;import vn.covie.users.model.*;import vn.covie.users.api.dto.*;import vn.covie.media.model.*;import vn.covie.media.persistence.MediaAssetRepository;
class UserProfileServiceIntegrationTest extends PostgresIntegrationTest {
 @Autowired UserProfileService profiles;@Autowired UserRepository users;@Autowired MediaAssetRepository media;@MockitoBean CurrentActorProvider actors;
 @Autowired UserIdentityRepository identities;
 UUID user;Instant now=Instant.now();
 @BeforeEach void setup(){user=UUID.randomUUID();users.saveAndFlush(UserEntity.active(user,"Profile user",now));when(actors.currentActor()).thenReturn(new CurrentActor(user,Set.of(SystemRole.USER),UUID.randomUUID()));}
 MediaAssetEntity asset(UUID owner,MediaPurpose purpose,boolean ready){var asset=MediaAssetEntity.pending(UUID.randomUUID(),owner,"avatars/"+UUID.randomUUID(),purpose,"image/png",8,now.plusSeconds(600),now);if(ready)asset.markReady(now);return media.saveAndFlush(asset);}
 @Test void namePhoneAndOwnedAvatarRoundtripOmissionPreservesAndExplicitNullClears(){
  var avatar=asset(user,MediaPurpose.USER_AVATAR,true);var request=new UpdateMeRequest(" Minh ");request.setPhone(" +84 901 234 567 ");request.setAvatarMediaId(avatar.getId());
  var changed=profiles.update(request);assertThat(changed.displayName()).isEqualTo("Minh");assertThat(changed.phone()).isEqualTo("+84 901 234 567");assertThat(changed.avatarMediaId()).isEqualTo(avatar.getId());assertThat(changed.avatarUrl()).isEqualTo("https://media.test.covie/"+avatar.getStorageKey());
  var reloaded=profiles.me();assertThat(reloaded.phone()).isEqualTo(changed.phone());assertThat(reloaded.avatarMediaId()).isEqualTo(avatar.getId());
  profiles.update(new UpdateMeRequest("New name"));assertThat(profiles.me().phone()).isEqualTo(changed.phone());assertThat(profiles.me().avatarMediaId()).isEqualTo(avatar.getId());
  var clear=new UpdateMeRequest("New name");clear.setPhone(null);clear.setAvatarMediaId(null);profiles.update(clear);assertThat(profiles.me().phone()).isNull();assertThat(profiles.me().avatarMediaId()).isNull();assertThat(users.findById(user).orElseThrow().getAvatarMediaId()).isNull();
 }
 @Test void refusesForeignPendingOrWrongPurposeAvatarAndInvalidPhoneWithoutChangingName(){
  UUID other=UUID.randomUUID();users.saveAndFlush(UserEntity.active(other,"Other",now));
  for(var avatar:List.of(asset(other,MediaPurpose.USER_AVATAR,true),asset(user,MediaPurpose.USER_AVATAR,false),asset(user,MediaPurpose.CATALOG_IMAGE,true))){var request=new UpdateMeRequest("Should not save");request.setAvatarMediaId(avatar.getId());assertThatThrownBy(()->profiles.update(request)).isInstanceOf(vn.covie.shared.web.ApiException.class);}
  var invalid=new UpdateMeRequest("Should not save");invalid.setPhone("not a phone");assertThatThrownBy(()->profiles.update(invalid)).isInstanceOf(vn.covie.shared.web.ApiException.class);assertThat(profiles.me().displayName()).isEqualTo("Profile user");
 }
 @Test void editingPublicProfilePreservesGoogleAuthenticationIdentity(){
  UUID identity=UUID.randomUUID();identities.saveAndFlush(UserIdentityEntity.google(identity,user,"subject-"+user,"google-"+user+"@example.test",now));
  var request=new UpdateMeRequest("Custom name");request.setPhone("0901234567");profiles.update(request);
  var saved=identities.findById(identity).orElseThrow();assertThat(saved.getProvider()).isEqualTo(IdentityProvider.GOOGLE);assertThat(saved.getProviderSubject()).isEqualTo("subject-"+user);assertThat(saved.getEmailNormalized()).isEqualTo("google-"+user+"@example.test");assertThat(saved.getPasswordHash()).isNull();
 }
}
