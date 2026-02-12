package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives account-level roles from assigned Spring Security role entities.
 */
@Service
public class AccountRoleService {
    /**
     * Resolves the highest-privilege account role for a user.
     *
     * @param user user entity with role assignments
     * @return resolved account role
     */
    public AccountRole resolveAccountRole(AppUser user) {
        Set<String> roleNames = user.getRoles().stream()
            .map(Role::getName)
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

        if (roleNames.contains(AccountRole.ADMIN.getValue())) {
            return AccountRole.ADMIN;
        }
        if (roleNames.contains(AccountRole.PARTNER.getValue())) {
            return AccountRole.PARTNER;
        }
        return AccountRole.CLIENT;
    }

    /**
     * Indicates whether a user can perform partner/admin level operations.
     *
     * @param user user entity with role assignments
     * @return {@code true} for partner or admin users
     */
    public boolean isPartnerOrAdmin(AppUser user) {
        AccountRole role = resolveAccountRole(user);
        return role == AccountRole.ADMIN || role == AccountRole.PARTNER;
    }
}
