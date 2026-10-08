package vn.covie.users.application;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.support.PostgresIntegrationTest;import vn.covie.auth.security.CurrentActorProvider;import vn.covie.shared.security.*;import vn.covie.users.persistence.*;import vn.covie.users.model.*;import vn.covie.users.api.dto.*;
class UserAddressServiceIntegrationTest extends PostgresIntegrationTest {
 @Autowired UserAddressService service;@Autowired UserRepository users;@Autowired UserAddressRepository addresses;@MockitoBean CurrentActorProvider actors;
 @Test void defaultSelectionPersistsAcrossSeparateServiceTransactionsAndExplicitFalseClearsIt(){
  UUID user=UUID.randomUUID();users.saveAndFlush(UserEntity.active(user,"Address user",Instant.now()));when(actors.currentActor()).thenReturn(new CurrentActor(user,Set.of(SystemRole.USER),UUID.randomUUID()));
  var first=service.create(new CreateUserAddressRequest("Home",null,null,"1 Street",10.8,106.8,PinSource.GPS,null,false));assertThat(addresses.findById(first.getId()).orElseThrow().isDefaultAddress()).isTrue();
  var second=service.create(new CreateUserAddressRequest("Office",null,null,"2 Street",10.9,106.9,PinSource.GPS,null,true));assertThat(addresses.findById(second.getId()).orElseThrow().isDefaultAddress()).isTrue();assertThat(addresses.findById(first.getId()).orElseThrow().isDefaultAddress()).isFalse();
  service.update(second.getId(),new UpdateUserAddressRequest("Office",null,null,"2 Street",10.9,106.9,PinSource.GPS,null,false));assertThat(addresses.findById(second.getId()).orElseThrow().isDefaultAddress()).isFalse();
  assertThat(addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(user,"ACTIVE")).noneMatch(UserAddressEntity::isDefaultAddress);
 }
}
