package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.UserDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private UserDeletionService userDeletionService;

    @BeforeEach
    void setUp() {
        userDeletionService = new UserDeletionService(
            appUserRepository,
            authSessionRepository,
            accountRoleService,
            transactionTemplate
        );
        lenient().doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void queueUserRejectsWhenQueueIsFull() {
        for (long id = 1; id <= 50; id++) {
            userDeletionService.queueUser(user(id, "user" + id + "@example.com"), AccountRole.CLIENT);
        }

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> userDeletionService.queueUser(user(99L, "overflow@example.com"), AccountRole.CLIENT)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("User deletion queue is full", ex.getReason());
    }

    @Test
    void restoreUserRemovesQueuedEntry() {
        userDeletionService.queueUser(user(7L, "queued@example.com"), AccountRole.CLIENT);

        userDeletionService.restoreUser(7L);

        assertEquals(Set.of(), userDeletionService.findQueuedUserIds(List.of(7L)));
    }

    @Test
    void processPendingDeletionsDeletesOnlyNonAdminUsersAndClearsProcessedEntries() {
        AppUser client = user(7L, "client@example.com");
        AppUser admin = user(9L, "admin@example.com");
        userDeletionService.queueUser(client, AccountRole.CLIENT);
        userDeletionService.queueUser(admin, AccountRole.PARTNER);

        when(appUserRepository.findByIdIn(List.of(7L, 9L))).thenReturn(List.of(client, admin));
        when(accountRoleService.resolveAccountRole(client)).thenReturn(AccountRole.CLIENT);
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);

        userDeletionService.processPendingDeletions();

        verify(authSessionRepository).deleteAllByUserIdIn(List.of(7L));
        verify(appUserRepository).deleteAllByIdInBatch(List.of(7L));
        verify(appUserRepository).flush();
        assertEquals(Set.of(), userDeletionService.findQueuedUserIds(List.of(7L, 9L)));
    }

    @Test
    void processPendingDeletionsRetainsQueueWhenDeletionFails() {
        AppUser client = user(7L, "client@example.com");
        userDeletionService.queueUser(client, AccountRole.CLIENT);

        when(appUserRepository.findByIdIn(List.of(7L))).thenReturn(List.of(client));
        when(accountRoleService.resolveAccountRole(client)).thenReturn(AccountRole.CLIENT);
        doThrow(new RuntimeException("boom")).when(appUserRepository).deleteAllByIdInBatch(eq(List.of(7L)));

        userDeletionService.processPendingDeletions();

        verify(appUserRepository, never()).flush();
        assertEquals(Set.of(7L), userDeletionService.findQueuedUserIds(List.of(7L)));
    }

    private AppUser user(Long id, String email) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        return user;
    }
}
