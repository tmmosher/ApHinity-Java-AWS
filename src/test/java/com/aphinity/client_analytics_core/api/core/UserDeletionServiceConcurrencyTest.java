package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.UserDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceConcurrencyTest {
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
    void processPendingDeletionsKeepsUsersRequeuedDuringProcessing() {
        AppUser client = user(7L, "client@example.com");
        userDeletionService.queueUser(client, AccountRole.CLIENT);

        when(appUserRepository.findByIdIn(List.of(7L))).thenReturn(List.of(client));
        when(accountRoleService.resolveAccountRole(client)).thenReturn(AccountRole.CLIENT);
        doAnswer(invocation -> {
            userDeletionService.queueUser(user(7L, "client@example.com"), AccountRole.CLIENT);
            return null;
        }).when(appUserRepository).deleteAllByIdInBatch(eq(List.of(7L)));

        userDeletionService.processPendingDeletions();

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
