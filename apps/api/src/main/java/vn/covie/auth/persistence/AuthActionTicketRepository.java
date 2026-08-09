package vn.covie.auth.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.auth.model.AuthActionTicketEntity;

public interface AuthActionTicketRepository extends JpaRepository<AuthActionTicketEntity, UUID> { Optional<AuthActionTicketEntity> findByTokenHash(String tokenHash); }
