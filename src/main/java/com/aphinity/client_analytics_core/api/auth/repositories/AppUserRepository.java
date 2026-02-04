package com.aphinity.client_analytics_core.api.auth.repositories;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmail(String email);
}
