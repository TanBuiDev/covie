package vn.covie.users.application;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.support.PostgresIntegrationTest;import vn.covie.auth.security.CurrentActorProvider;import vn.covie.shared.security.*;import vn.covie.users.persistence.*;import vn.covie.users.model.*;import vn.covie.users.api.dto.*;
class UserAddressServiceIntegrationTest extends PostgresIntegrationTest {
 @Autowired UserAddressService service;@Autowired UserRepository users;@Autowired UserAddressRepository addresses;@MockitoBean CurrentActorProvider actors;
 @Test void foreignActorCannotReadUpdateOrDeleteAnAddress(){
  UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
  users.saveAndFlush(UserEntity.active(owner,"Owner",Instant.now()));users.saveAndFlush(UserEntity.active(other,"Other",Instant.now()));
  when(actors.currentActor()).thenReturn(new CurrentActor(owner,Set.of(SystemRole.USER),UUID.randomUUID()));
  var saved=service.create(new CreateUserAddressRequest("Home",null,null,"Original address",10.8,106.8,PinSource.GPS,null,true));
  when(actors.currentActor()).thenReturn(new CurrentActor(other,Set.of(SystemRole.USER),UUID.randomUUID()));
  var replacement=new UpdateUserAddressRequest("Changed",null,null,"Changed address",10.9,106.9,PinSource.GPS,null,true);
  for(Runnable action:List.<Runnable>of(()->service.get(saved.getId()),()->service.update(saved.getId(),replacement),()->service.delete(saved.getId()))){
   assertThatThrownBy(action::run).isInstanceOfSatisfying(vn.covie.shared.web.ApiException.class,error->{
    assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    assertThat(error.code()).isEqualTo(vn.covie.shared.web.ErrorCode.RESOURCE_NOT_FOUND);
   });
  }
  assertThat(service.active()).isEmpty();
  when(actors.currentActor()).thenReturn(new CurrentActor(owner,Set.of(SystemRole.USER),UUID.randomUUID()));
  var unchanged=service.get(saved.getId());assertThat(unchanged.getAddressText()).isEqualTo("Original address");assertThat(unchanged.isDefaultAddress()).isTrue();
 }

 @Test void deletingDefaultPromotesAnOwnedActiveReplacementAcrossTransactions(){
  UUID user=UUID.randomUUID();users.saveAndFlush(UserEntity.active(user,"Owner",Instant.now()));
  when(actors.currentActor()).thenReturn(new CurrentActor(user,Set.of(SystemRole.USER),UUID.randomUUID()));
  var first=service.create(new CreateUserAddressRequest("Home",null,null,"1 Street",10.8,106.8,PinSource.GPS,null,true));
  var second=service.create(new CreateUserAddressRequest("Office",null,null,"2 Street",10.9,106.9,PinSource.GPS,null,false));
  service.delete(first.getId());
  assertThat(service.active()).singleElement().satisfies(address->{assertThat(address.getId()).isEqualTo(second.getId());assertThat(address.isDefaultAddress()).isTrue();});
  assertThatThrownBy(()->service.get(first.getId())).isInstanceOf(vn.covie.shared.web.ApiException.class);
 }
 @Test void defaultSelectionPersistsAcrossSeparateServiceTransactionsAndExplicitFalseClearsIt(){
  UUID user=UUID.randomUUID();users.saveAndFlush(UserEntity.active(user,"Address user",Instant.now()));when(actors.currentActor()).thenReturn(new CurrentActor(user,Set.of(SystemRole.USER),UUID.randomUUID()));
  var first=service.create(new CreateUserAddressRequest("Home",null,null,"1 Street",10.8,106.8,PinSource.GPS,null,false));assertThat(addresses.findById(first.getId()).orElseThrow().isDefaultAddress()).isTrue();
  var second=service.create(new CreateUserAddressRequest("Office",null,null,"2 Street",10.9,106.9,PinSource.GPS,null,true));assertThat(addresses.findById(second.getId()).orElseThrow().isDefaultAddress()).isTrue();assertThat(addresses.findById(first.getId()).orElseThrow().isDefaultAddress()).isFalse();
  service.update(second.getId(),new UpdateUserAddressRequest("Office",null,null,"2 Street",10.9,106.9,PinSource.GPS,null,false));assertThat(addresses.findById(second.getId()).orElseThrow().isDefaultAddress()).isFalse();
  assertThat(addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(user,"ACTIVE")).noneMatch(UserAddressEntity::isDefaultAddress);
 }
}
