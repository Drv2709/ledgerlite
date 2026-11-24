package com.ledgerlite.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Used by the admin API to list everyone except one user (the internal SYSTEM user). */
    Page<User> findByIdNot(UUID id, Pageable pageable);
}
