package com.aphinity.client_analytics_core.api.auth.repositories;

import com.aphinity.client_analytics_core.api.auth.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
