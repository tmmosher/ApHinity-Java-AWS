package com.aphinity.client_analytics_core.api.auth.repositories;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmail(String email);

    @Query("select u.id from AppUser u order by lower(u.email) asc, u.id asc")
    Page<Long> findPagedUserIds(Pageable pageable);

    @EntityGraph(attributePaths = "roles")
    List<AppUser> findByIdIn(Collection<Long> ids);
}
