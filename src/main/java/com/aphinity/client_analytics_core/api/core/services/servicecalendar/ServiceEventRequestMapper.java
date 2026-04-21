package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

@Service
public class ServiceEventRequestMapper {
    public ServiceEvent createServiceEvent(Location location, LocationEventRequest request) {
        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setLocation(location);
        applyRequest(serviceEvent, request);
        return serviceEvent;
    }

    public void applyRequest(ServiceEvent serviceEvent, LocationEventRequest request) {
        LocalDate eventDate = normalizeDate(request == null ? null : request.date());
        LocalTime eventTime = normalizeTime(request == null ? null : request.time());
        LocalDate endEventDate = normalizeEndDate(eventDate, request == null ? null : request.endDate());
        LocalTime endEventTime = normalizeEndTime(eventTime, request == null ? null : request.endTime());
        validateEventRange(eventDate, eventTime, endEventDate, endEventTime);

        serviceEvent.setTitle(normalizeTitle(request == null ? null : request.title()));
        serviceEvent.setResponsibility(normalizeResponsibility(request == null ? null : request.responsibility()));
        serviceEvent.setEventDate(eventDate);
        serviceEvent.setEventTime(eventTime);
        serviceEvent.setEndEventDate(endEventDate);
        serviceEvent.setEndEventTime(endEventTime);
        serviceEvent.setDescription(normalizeDescription(request == null ? null : request.description()));
        serviceEvent.setStatus(normalizeStatus(request == null ? null : request.status()));
    }

    public ServiceEventResponse toResponse(ServiceEvent serviceEvent) {
        ServiceEvent sourceEvent = serviceEvent.getCorrectiveActionSourceEvent();
        return new ServiceEventResponse(
            serviceEvent.getId(),
            serviceEvent.getTitle(),
            serviceEvent.getResponsibility(),
            serviceEvent.getEventDate(),
            serviceEvent.getEventTime(),
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime(),
            serviceEvent.getDescription(),
            serviceEvent.getStatus(),
            serviceEvent.isCorrectiveAction(),
            sourceEvent == null ? null : sourceEvent.getId(),
            sourceEvent == null ? null : sourceEvent.getTitle(),
            serviceEvent.getCreatedAt(),
            serviceEvent.getUpdatedAt()
        );
    }

    public ServiceEventResponsibility requireResponsibility(LocationEventRequest request) {
        return normalizeResponsibility(request == null ? null : request.responsibility());
    }

    public YearMonth requireViewedMonth(YearMonth viewedMonth) {
        if (viewedMonth == null) {
            throw invalidViewedMonth();
        }
        return viewedMonth;
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            throw invalidEventTitle();
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidEventTitle();
        }
        if (normalized.length() > ServiceEvent.TITLE_MAX_LENGTH) {
            throw invalidEventTitleLength();
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private ServiceEventResponsibility normalizeResponsibility(ServiceEventResponsibility responsibility) {
        if (responsibility == null) {
            throw invalidEventResponsibility();
        }
        return responsibility;
    }

    private LocalDate normalizeDate(LocalDate date) {
        if (date == null) {
            throw invalidEventDate();
        }
        return date;
    }

    private LocalTime normalizeTime(LocalTime time) {
        if (time == null) {
            throw invalidEventTime();
        }
        return time;
    }

    private LocalDate normalizeEndDate(LocalDate eventDate, LocalDate endDate) {
        if (endDate == null) {
            return eventDate;
        }
        return endDate;
    }

    private LocalTime normalizeEndTime(LocalTime eventTime, LocalTime endTime) {
        if (endTime == null) {
            return eventTime;
        }
        return endTime;
    }

    private void validateEventRange(
        LocalDate eventDate,
        LocalTime eventTime,
        LocalDate endEventDate,
        LocalTime endEventTime
    ) {
        LocalDateTime startDateTime = LocalDateTime.of(eventDate, eventTime);
        LocalDateTime endDateTime = LocalDateTime.of(endEventDate, endEventTime);
        if (endDateTime.isBefore(startDateTime)) {
            throw invalidEventRange();
        }
    }

    private ServiceEventStatus normalizeStatus(ServiceEventStatus status) {
        if (status == null) {
            throw invalidEventStatus();
        }
        return status;
    }

    private ResponseStatusException invalidEventTitle() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event title is required");
    }

    private ResponseStatusException invalidEventTitleLength() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event title must be 42 characters or fewer");
    }

    private ResponseStatusException invalidEventResponsibility() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event responsibility is required");
    }

    private ResponseStatusException invalidEventDate() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event date is required");
    }

    private ResponseStatusException invalidEventTime() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event time is required");
    }

    private ResponseStatusException invalidEventStatus() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event status is required");
    }

    private ResponseStatusException invalidViewedMonth() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Viewed month is required");
    }

    private ResponseStatusException invalidEventRange() {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Event end must be on or after the start date and time"
        );
    }
}
