package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.NotificationEventRequest;
import com.traveleasy.booking.dto.RecipientDto;
import com.traveleasy.booking.dto.RecipientRole;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.BookingOutboxEvent;
import com.traveleasy.booking.repository.BookingOutboxEventRepository;
import com.traveleasy.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingStateTransitionServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingOutboxEventRepository outboxRepository;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private BookingStateTransitionService transitionService;

    @BeforeEach
    void setUp() {
        transitionService = new BookingStateTransitionService(bookingRepository, outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("confirmBooking transitions PENDING_PAYMENT to CONFIRMED and records BUS_BOOKING_CONFIRMED outbox event")
    void testConfirmBooking_Success() throws Exception {
        Booking booking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-BUS-1001")
                .customerId(42L)
                .customerName("Jane Traveller")
                .customerEmail("jane@example.com")
                .providerId(101L)
                .operatorBusinessName("Orange Travels")
                .serviceTitle("Hyderabad to Vijayawada - Bus")
                .totalAmount(1200.00)
                .status("PENDING_PAYMENT")
                .detailsJson("Bus: Express | Seats: A1, A2")
                .bookingDate(LocalDateTime.now())
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = transitionService.confirmBooking(booking);

        assertEquals("CONFIRMED", result.getStatus());
        verify(bookingRepository).save(booking);

        ArgumentCaptor<BookingOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(BookingOutboxEvent.class);
        verify(outboxRepository).saveAndFlush(outboxCaptor.capture());

        BookingOutboxEvent outboxEvent = outboxCaptor.getValue();
        assertEquals("BKG-EVT-BKG-BUS-1001-CONFIRMED", outboxEvent.getEventId());
        assertEquals("BUS_BOOKING_CONFIRMED", outboxEvent.getEventType());
        assertEquals("BOOKING", outboxEvent.getAggregateType());
        assertEquals("BKG-BUS-1001", outboxEvent.getAggregateId());
        assertEquals("PENDING", outboxEvent.getStatus());

        NotificationEventRequest payload = objectMapper.readValue(outboxEvent.getPayloadJson(), NotificationEventRequest.class);
        assertEquals("BUS_BOOKING_CONFIRMED", payload.getEventType());
        assertEquals(2, payload.getRecipients().size());

        RecipientDto traveller = payload.getRecipients().get(0);
        assertEquals(RecipientRole.TRAVELLER, traveller.getRole());
        assertEquals(42L, traveller.getUserId());
        assertEquals("jane@example.com", traveller.getEmail());
        assertTrue(traveller.getChannels().contains("IN_APP"));
        assertTrue(traveller.getChannels().contains("EMAIL"));

        RecipientDto operator = payload.getRecipients().get(1);
        assertEquals(RecipientRole.BUS_OPERATOR, operator.getRole());
        assertEquals(101L, operator.getUserId());
        assertEquals("Orange Travels", operator.getName());
        assertEquals(List.of("IN_APP"), operator.getChannels());

        assertEquals("bus-booking-confirmation", payload.getContent().getTemplate());
        assertEquals("BKG-BUS-1001", payload.getContent().getTemplateData().get("bookingRef"));
        assertEquals("1200.00", payload.getContent().getTemplateData().get("totalFare"));
        assertEquals("A1, A2", payload.getContent().getTemplateData().get("seats"));
    }

    @Test
    @DisplayName("confirmBooking with null operator business name uses neutral fallback 'Bus Operator' without parsing title")
    void testConfirmBooking_NeutralOperatorFallback() throws Exception {
        Booking booking = Booking.builder()
                .id(2L)
                .bookingRef("BKG-BUS-1002")
                .customerId(43L)
                .customerName("Bob")
                .customerEmail("bob@example.com")
                .providerId(202L)
                .operatorBusinessName(null) // Not populated
                .serviceTitle("Special Super Deluxe Bus Express")
                .totalAmount(800.00)
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        transitionService.confirmBooking(booking);

        ArgumentCaptor<BookingOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(BookingOutboxEvent.class);
        verify(outboxRepository).saveAndFlush(outboxCaptor.capture());

        NotificationEventRequest payload = objectMapper.readValue(outboxCaptor.getValue().getPayloadJson(), NotificationEventRequest.class);
        RecipientDto operator = payload.getRecipients().get(1);
        assertEquals("Bus Operator", operator.getName());
    }

    @Test
    @DisplayName("confirmBooking on already CONFIRMED booking is idempotent early return")
    void testConfirmBooking_AlreadyConfirmed_NoOp() {
        Booking booking = Booking.builder()
                .id(1L)
                .bookingRef("BKG-BUS-1001")
                .status("CONFIRMED")
                .build();

        Booking result = transitionService.confirmBooking(booking);

        assertEquals("CONFIRMED", result.getStatus());
        verify(bookingRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelBooking on CONFIRMED booking transitions to CANCELLED and records BUS_BOOKING_CANCELLED event")
    void testCancelBooking_Confirmed_EmitsOutboxEvent() throws Exception {
        Booking booking = Booking.builder()
                .id(3L)
                .bookingRef("BKG-BUS-1003")
                .customerId(45L)
                .customerName("Alice")
                .customerEmail("alice@example.com")
                .providerId(303L)
                .totalAmount(1500.00)
                .status("CONFIRMED")
                .detailsJson("Seats: 12A")
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = transitionService.cancelBooking(booking, 1500.00, "Customer cancellation");

        assertEquals("CANCELLED", result.getStatus());
        assertEquals(1500.00, result.getRefundAmount());

        ArgumentCaptor<BookingOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(BookingOutboxEvent.class);
        verify(outboxRepository).saveAndFlush(outboxCaptor.capture());

        BookingOutboxEvent outboxEvent = outboxCaptor.getValue();
        assertEquals("BKG-EVT-BKG-BUS-1003-CANCELLED", outboxEvent.getEventId());
        assertEquals("BUS_BOOKING_CANCELLED", outboxEvent.getEventType());

        NotificationEventRequest payload = objectMapper.readValue(outboxEvent.getPayloadJson(), NotificationEventRequest.class);
        assertEquals("bus-booking-cancelled", payload.getContent().getTemplate());
        assertEquals("1500.00", payload.getContent().getTemplateData().get("refundAmount"));
    }

    @Test
    @DisplayName("cancelBooking on unpaid PENDING_PAYMENT booking transitions to CANCELLED without outbox event")
    void testCancelBooking_Unpaid_NoOutboxEvent() {
        Booking booking = Booking.builder()
                .id(4L)
                .bookingRef("BKG-BUS-1004")
                .customerId(46L)
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = transitionService.cancelBooking(booking, null, "User cancelled unpaid hold");

        assertEquals("CANCELLED", result.getStatus());
        verify(bookingRepository).save(booking);
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelBooking on already CANCELLED booking is idempotent early return")
    void testCancelBooking_AlreadyCancelled_NoOp() {
        Booking booking = Booking.builder()
                .id(5L)
                .bookingRef("BKG-BUS-1005")
                .status("CANCELLED")
                .build();

        Booking result = transitionService.cancelBooking(booking, null, "Duplicate cancel");

        assertEquals("CANCELLED", result.getStatus());
        verify(bookingRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("confirmBooking handles DataIntegrityViolationException on concurrent duplicate outbox insertion safely")
    void testConfirmBooking_HandlesConcurrentDuplicateOutbox() {
        Booking booking = Booking.builder()
                .id(6L)
                .bookingRef("BKG-BUS-1006")
                .customerId(50L)
                .customerEmail("user@example.com")
                .status("PENDING_PAYMENT")
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate key UK_outbox_event_id"));

        assertDoesNotThrow(() -> transitionService.confirmBooking(booking));
        assertEquals("CONFIRMED", booking.getStatus());
    }
}
