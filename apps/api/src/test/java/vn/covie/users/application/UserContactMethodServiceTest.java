package vn.covie.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.ContactType;
import vn.covie.users.model.UserContactMethodEntity;
import vn.covie.users.persistence.UserContactMethodRepository;

class UserContactMethodServiceTest {
    @Test
    void creatingPrimaryFirstClearsExistingPrimaryOfTheSameType() {
        UUID userId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        UserContactMethodRepository contacts = mock(UserContactMethodRepository.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(userId, Set.of(SystemRole.USER), UUID.randomUUID()));
        UserContactMethodEntity existing = UserContactMethodEntity.create(UUID.randomUUID(), userId, ContactType.PHONE, "0900000000", true, Instant.EPOCH);
        when(contacts.findAllByUserIdAndContactTypeAndStatus(userId, ContactType.PHONE, "ACTIVE")).thenReturn(List.of(existing));
        when(contacts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserContactMethodService service = new UserContactMethodService(actors, contacts, () -> Instant.parse("2026-08-09T00:00:00Z"));

        UserContactMethodEntity created = service.create(ContactType.PHONE, " 0911111111 ", true);

        assertThat(existing.isPrimary()).isFalse();
        assertThat(created.isPrimary()).isTrue();
        assertThat(created.getValue()).isEqualTo("0911111111");
        verify(contacts).flush();
    }

    @Test
    void hidesAnotherUsersContactAsNotFound() {
        UUID userId = UUID.randomUUID(); UUID contactId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        UserContactMethodRepository contacts = mock(UserContactMethodRepository.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(userId, Set.of(SystemRole.USER), UUID.randomUUID()));
        when(contacts.findByIdAndUserId(contactId, userId)).thenReturn(java.util.Optional.empty());
        UserContactMethodService service = new UserContactMethodService(actors, contacts, Instant::now);

        assertThatThrownBy(() -> service.delete(contactId)).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
