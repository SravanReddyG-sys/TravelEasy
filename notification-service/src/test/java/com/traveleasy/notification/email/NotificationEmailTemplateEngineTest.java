package com.traveleasy.notification.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationEmailTemplateEngineTest {

    private final NotificationEmailTemplateEngine engine = new NotificationEmailTemplateEngine();

    @Test
    @DisplayName("Renders hotel-booking-cancelled HTML template with hotel name, refund amount, cancellation fee, and summary")
    void testRenderHotelBookingCancelledHtml() {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingRef", "BKG-HTL-2026-991");
        data.put("hotelName", "GSR Grand & Premium <Resort>");
        data.put("checkInDate", "2026-12-10");
        data.put("checkOutDate", "2026-12-12");
        data.put("refundAmount", "2500.00");
        data.put("cancellationFee", "2500.00");
        data.put("refundStatus", "PENDING");
        data.put("summary", "Your hotel booking has been cancelled with a 50% refund.");

        String html = engine.renderHtml("hotel-booking-cancelled", "Hotel Booking Cancelled", data);

        assertNotNull(html);
        assertTrue(html.contains("Hotel Booking Cancelled"));
        assertTrue(html.contains("BKG-HTL-2026-991"));
        assertTrue(html.contains("GSR Grand &amp; Premium &lt;Resort&gt;"), "Must HTML-escape hotel name");
        assertTrue(html.contains("2026-12-10 to 2026-12-12"));
        assertTrue(html.contains("&#8377;2500.00"));
        assertTrue(html.contains("PENDING"));
        assertTrue(html.contains("Your hotel booking has been cancelled with a 50% refund."));
    }

    @Test
    @DisplayName("Renders hotel-booking-cancelled HTML template cleanly with minimal fields and default summary fallback")
    void testRenderHotelBookingCancelledHtmlMinimalFallback() {
        Map<String, Object> data = Map.of(
                "bookingRef", "BKG-HTL-MIN-1",
                "hotelName", "Lakeside Stay"
        );

        String html = engine.renderHtml("hotel-booking-cancelled", "Hotel Booking Cancelled", data);

        assertTrue(html.contains("BKG-HTL-MIN-1"));
        assertTrue(html.contains("Lakeside Stay"));
        assertTrue(html.contains("Your hotel reservation has been cancelled."));
    }
}
