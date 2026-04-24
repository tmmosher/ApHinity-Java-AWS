package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateRequest;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdatePayloadValidationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTransactionTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private LocationGraphRepository locationGraphRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private UserSubscriptionToLocationRepository userSubscriptionToLocationRepository;

    @Mock
    private AccountRoleService accountRoleService;

    private LocationService locationService;

    private LocationService proxiedLocationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(
            appUserRepository,
            locationRepository,
            graphRepository,
            locationGraphRepository,
            locationUserRepository,
            userSubscriptionToLocationRepository,
            accountRoleService,
            new LocationThumbnailImageService(),
            new LocationGraphTemplateFactory(),
            new LocationGraphUpdatePayloadValidationFactory()
        );

        ProxyFactory proxyFactory = new ProxyFactory(locationService);
        proxyFactory.addAdvice(
            new TransactionInterceptor(
                new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()
            )
        );
        proxiedLocationService = (LocationService) proxyFactory.getProxy();
    }

    @Test
    void updateLocationGraphDataRunsLockedGraphLookupInsideTransaction() {
        AppUser user = verifiedUser(2L);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(1L), anyCollection()))
            .thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return List.of(graph);
            });

        proxiedLocationService.updateLocationGraphData(
            2L,
            1L,
            List.of(new LocationGraphDataUpdateRequest(
                31L,
                List.of(Map.of("type", "bar", "y", List.of(9, 8, 7)))
            )),
            null
        );

        verify(graphRepository).saveAll(List.of(graph));
        verify(locationRepository).touchUpdatedAt(eq(1L), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    private static AppUser verifiedUser(Long userId) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("user" + userId + "@example.com");
        user.setEmailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private static final class TestTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.initSynchronization();
            }
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Override
        public void rollback(TransactionStatus status) {
            commit(status);
        }
    }
}
