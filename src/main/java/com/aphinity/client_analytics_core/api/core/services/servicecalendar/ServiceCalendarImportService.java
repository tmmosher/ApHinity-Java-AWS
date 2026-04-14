package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServiceCalendarImportService {
    private static final Logger log = LoggerFactory.getLogger(ServiceCalendarImportService.class);

    private final LocationRepository locationRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final ServiceCalendarSpreadsheetParser serviceCalendarSpreadsheetParser;
    private final ServiceCalendarAuthorizationService authorizationService;
    private final ServiceEventRequestMapper requestMapper;
    private final ServiceEventAuditService auditService;

    public ServiceCalendarImportService(
        LocationRepository locationRepository,
        ServiceEventRepository serviceEventRepository,
        ServiceCalendarSpreadsheetParser serviceCalendarSpreadsheetParser,
        ServiceCalendarAuthorizationService authorizationService,
        ServiceEventRequestMapper requestMapper,
        ServiceEventAuditService auditService
    ) {
        this.locationRepository = locationRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.serviceCalendarSpreadsheetParser = serviceCalendarSpreadsheetParser;
        this.authorizationService = authorizationService;
        this.requestMapper = requestMapper;
        this.auditService = auditService;
    }

    @Transactional
    public int uploadServiceCalendar(Long userId, Long locationId, MultipartFile file) {
        AppUser user = authorizationService.requireUser(userId);
        Location location = authorizationService.requireLocation(locationId);
        authorizationService.requireReadableLocationAccess(user, locationId);

        List<ServiceCalendarSpreadsheetParser.ParsedServiceCalendarRow> parsedRows =
            serviceCalendarSpreadsheetParser.parse(file);
        List<ServiceEvent> serviceEvents = new ArrayList<>(parsedRows.size());

        for (ServiceCalendarSpreadsheetParser.ParsedServiceCalendarRow parsedRow : parsedRows) {
            try {
                ServiceEventResponsibility responsibility = requestMapper.requireResponsibility(parsedRow.request());
                authorizationService.requireCreatePermission(user, locationId, responsibility);
                serviceEvents.add(requestMapper.createServiceEvent(location, parsedRow.request()));
            } catch (ResponseStatusException ex) {
                throw spreadsheetRowInvalid(parsedRow.rowNumber(), ex.getReason());
            }
        }

        try {
            serviceEventRepository.saveAllAndFlush(serviceEvents);
            for (ServiceEvent serviceEvent : serviceEvents) {
                auditService.recordCreated(userId, serviceEvent);
            }
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return serviceEvents.size();
        } catch (RuntimeException ex) {
            log.error(
                "Service calendar upload persistence failed actorUserId={} locationId={} importedCount={}",
                userId,
                locationId,
                serviceEvents.size(),
                ex
            );
            throw ex;
        }
    }

    private ApiClientException spreadsheetRowInvalid(int rowNumber, String reason) {
        String message = reason == null || reason.isBlank() ? "Request failed" : reason;
        return new ApiClientException(
            HttpStatus.BAD_REQUEST,
            "service_calendar_row_invalid",
            "Row " + rowNumber + ": " + message
        );
    }
}
