package vn.covie.users.application;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.ContactType;
import vn.covie.users.model.UserContactMethodEntity;
import vn.covie.users.persistence.UserContactMethodRepository;

@Service
public class UserContactMethodService {
    private static final String ACTIVE = "ACTIVE";
    private final CurrentActorProvider actors; private final UserContactMethodRepository contacts; private final AppClock clock;
    public UserContactMethodService(CurrentActorProvider actors, UserContactMethodRepository contacts, AppClock clock) { this.actors=actors; this.contacts=contacts; this.clock=clock; }
    @Transactional(readOnly = true)
    public List<UserContactMethodEntity> active() { return contacts.findAllByUserIdAndStatusOrderByCreatedAtAsc(actors.currentActor().userId(), ACTIVE); }
    @Transactional
    public UserContactMethodEntity create(ContactType type, String value, boolean primary) {
        UUID userId = actors.currentActor().userId(); UserContactMethodEntity contact = UserContactMethodEntity.create(UUID.randomUUID(), userId, type, value.trim(), false, clock.now());
        contacts.save(contact); if (primary) makePrimary(userId, contact); return contact;
    }
    @Transactional
    public UserContactMethodEntity update(UUID contactId, String value, boolean primary) {
        UUID userId = actors.currentActor().userId(); UserContactMethodEntity contact = owned(contactId, userId); contact.updateValue(value.trim());
        if (primary) makePrimary(userId, contact); else contact.setPrimary(false); return contact;
    }
    @Transactional
    public void delete(UUID contactId) { UserContactMethodEntity contact = owned(contactId, actors.currentActor().userId()); contact.deactivate(); }
    private void makePrimary(UUID userId, UserContactMethodEntity target) {
        contacts.findAllByUserIdAndContactTypeAndStatus(userId, target.getContactType(), ACTIVE).forEach(contact -> contact.setPrimary(false));
        contacts.flush(); target.setPrimary(true);
    }
    private UserContactMethodEntity owned(UUID id, UUID userId) { return contacts.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Contact method not found")); }
}
