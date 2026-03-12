package com.aphinity.client_analytics_core.api.auth.repositories;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmail(String email);

    @Query("""
        select u.id from AppUser u
        where not exists (
            select 1
            from AppUser adminCandidate
            join adminCandidate.roles role
            where adminCandidate.id = u.id
              and lower(role.name) = 'admin'
        )
        order by lower(coalesce(u.name, '')) asc, lower(u.email) asc, u.id asc
        """)
    Page<Long> findManagedUserIds(Pageable pageable);

    @Query("""
        select u.id from AppUser u
        where lower(u.email) like concat('%', lower(:query), '%')
          and not exists (
              select 1
              from AppUser adminCandidate
              join adminCandidate.roles role
              where adminCandidate.id = u.id
                and lower(role.name) = 'admin'
          )
        order by lower(u.email) asc, u.id asc
        """)
    Page<Long> searchManagedUserIdsByEmail(@Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = "roles")
    List<AppUser> findByIdIn(Collection<Long> ids);
}
