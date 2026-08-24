package vn.covie.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id private UUID id;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(name = "avatar_media_id") private UUID avatarMediaId;
    @Enumerated(EnumType.STRING) @Column(name = "account_status", nullable = false, length = 30) private AccountStatus accountStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private long version;

    protected UserEntity() {}

    public static UserEntity active(UUID id, String displayName, Instant now) {
        UserEntity user = new UserEntity(); user.id=id; user.displayName=displayName; user.accountStatus=AccountStatus.ACTIVE;
        user.createdAt=now; user.updatedAt=now; return user;
    }

    public void suspend(Instant now) {
        if (accountStatus != AccountStatus.ACTIVE) throw new IllegalStateException("User is not ACTIVE.");
        accountStatus = AccountStatus.SUSPENDED; updatedAt = now;
    }

    public void restore(Instant now) {
        if (accountStatus != AccountStatus.SUSPENDED) throw new IllegalStateException("User is not SUSPENDED.");
        accountStatus = AccountStatus.ACTIVE; updatedAt = now;
    }

    public boolean isActive() { return accountStatus == AccountStatus.ACTIVE; }
    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public UUID getAvatarMediaId() { return avatarMediaId; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public void updateProfile(String displayName, UUID avatarMediaId, Instant now) { this.displayName=displayName; this.avatarMediaId=avatarMediaId; this.updatedAt=now; }
    public void updateDisplayName(String displayName, Instant now) { this.displayName=displayName; this.updatedAt=now; }
    public void setAccountStatus(AccountStatus status, Instant now) { this.accountStatus=status; this.updatedAt=now; }
}
