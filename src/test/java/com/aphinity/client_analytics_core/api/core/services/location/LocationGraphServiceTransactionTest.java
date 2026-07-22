package com.aphinity.client_analytics_core.api.core.services.location;

import static com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper.readData;
import static com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper.writeData;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCacheInvalidationService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardMutationLockService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardProjectionService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardRefreshService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardTimeRangeService;
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
class LocationGraphServiceTransactionTest {
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
    private AccountRoleService accountRoleService;

    @Mock
    private LocationDashboardTimeRangeService locationDashboardTimeRangeService;

    private LocationGraphService graphService;

    private LocationGraphApplication proxiedGraphService;

    @BeforeEach
    void setUp() {
        LocationDashboardCacheInvalidationService invalidator =
            new LocationDashboardCacheInvalidationService(locationDashboardTimeRangeService);
        graphService = new LocationGraphService(
            appUserRepository,
            locationRepository,
            graphRepository,
            locationGraphRepository,
            locationUserRepository,
            accountRoleService,
            new LocationGraphTemplateFactory(List.copyOf(BuiltinLocationGraphDefinitions.defaults())),
            new LocationGraphUpdatePayloadValidationFactory(
                new com.aphinity.client_analytics_core.api.core.services.location.payload.CartesianTraceDateOrderCanonicalizer(),
                List.of(
                    new com.aphinity.client_analytics_core.api.core.services.location.payload.PieGraphPayloadValidator(),
                    new com.aphinity.client_analytics_core.api.core.services.location.payload.IndicatorGraphPayloadValidator(),
                    new com.aphinity.client_analytics_core.api.core.services.location.payload.CartesianGraphPayloadValidator(),
                    new com.aphinity.client_analytics_core.api.core.services.location.payload.TableGraphPayloadValidator(),
                    new com.aphinity.client_analytics_core.api.core.services.location.payload.SunburstGraphPayloadValidator()
                )
            ),
            new LocationDashboardMutationLockService(),
            new LocationDashboardProjectionService(locationDashboardTimeRangeService),
            new LocationDashboardRefreshService(locationDashboardTimeRangeService),
            invalidator,
            new GraphResponseMapper(new com.aphinity.client_analytics_core.api.core.plotly.RelationalPlotlyGraphPayloadAdapter()),
            new com.aphinity.client_analytics_core.api.core.plotly.RelationalPlotlyGraphPayloadAdapter()
        );

        ProxyFactory proxyFactory = new ProxyFactory(graphService);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(
            new TransactionInterceptor(
                new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()
            )
        );
        proxiedGraphService = (LocationGraphApplication) proxyFactory.getProxy();
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
        writeData(graph, List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(1L), anyCollection()))
            .thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return List.of(graph);
            });

        proxiedGraphService.updateLocationGraphs(
            2L,
            1L,
            List.of(new LocationGraphApplication.GraphUpdateCommand(
                31L,
                null,
                List.of(Map.of("type", "bar", "y", List.of(9, 8, 7))),
                null,
                null,
                null,
                null
            )),
            null,
            null
        );

        verify(graphRepository).saveAllAndFlush(List.of(graph));
        verify(locationRepository).touchUpdatedAt(eq(1L), org.mockito.ArgumentMatchers.any(Instant.class));
        verify(locationDashboardTimeRangeService).invalidateLocationCache(1L);
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
