package com.traveleasy.notification.email;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationEmailTemplateEngine {

    public String renderHtml(String templateName, String subject, Map<String, Object> data) {
        if (templateName == null) templateName = "default";
        return switch (templateName.toLowerCase()) {
            case "bus-booking-confirmation" -> renderBusBookingConfirmedHtml(subject, data);
            case "bus-booking-cancelled" -> renderBusBookingCancelledHtml(subject, data);
            case "payment-failed" -> renderPaymentFailedHtml(subject, data);
            case "payment-refund-initiated" -> renderRefundInitiatedHtml(subject, data);
            case "payment-refund-completed" -> renderRefundCompletedHtml(subject, data);
            default -> renderDefaultHtml(subject, data);
        };
    }

    public String renderPlainText(String templateName, String subject, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Travel Easy - ").append(subject).append("\n");
        sb.append("=========================================\n\n");
        if (data != null && !data.isEmpty()) {
            data.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        }
        sb.append("\nThank you for choosing Travel Easy.\n");
        return sb.toString();
    }

    private String getHeaderHtml() {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                "<body style=\"margin:0;padding:0;background-color:#F1F7FC;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;\">" +
                "<table role=\"presentation\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color:#F1F7FC;padding:32px 16px;\">" +
                "<tr><td align=\"center\">" +
                "<table role=\"presentation\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;background-color:#ffffff;border-radius:16px;border:1px solid #E2E8F0;box-shadow:0 4px 18px rgba(15,23,42,0.05);overflow:hidden;\">" +
                "<tr><td style=\"padding:24px 32px 0 32px;\">" +
                "<div style=\"font-size:22px;font-weight:800;color:#0F172A;\">Travel <span style=\"color:#0B74DE;\">Easy</span></div>" +
                "<div style=\"font-size:10px;font-weight:700;color:#64748B;letter-spacing:1px;text-transform:uppercase;\">Made Travel Easy</div>" +
                "<div style=\"height:1px;background-color:#E2E8F0;margin-top:16px;\"></div>" +
                "</td></tr><tr><td style=\"padding:24px 32px 32px 32px;\">";
    }

    private String getFooterHtml() {
        return "</td></tr>" +
                "<tr><td style=\"background-color:#F8FAFC;padding:16px 32px;border-top:1px solid #E2E8F0;text-align:center;\">" +
                "<p style=\"margin:0;font-size:12px;color:#94A3B8;\">Need help? Contact support@traveleasy.com</p>" +
                "<p style=\"margin:4px 0 0 0;font-size:11px;color:#CBD5E1;\">&copy; 2026 Travel Easy Inc. All rights reserved.</p>" +
                "</td></tr></table></td></tr></table></body></html>";
    }

    private String renderBusBookingConfirmedHtml(String subject, Map<String, Object> data) {
        String bookingRef = getVal(data, "bookingRef");
        String origin = getVal(data, "origin");
        String destination = getVal(data, "destination");
        String journeyDate = getVal(data, "journeyDate");
        String departureTime = getVal(data, "departureTime");
        String seats = getVal(data, "seats");
        String totalFare = getVal(data, "totalFare");
        String operator = getVal(data, "operatorBusinessName");

        return getHeaderHtml() +
                "<div style=\"display:inline-block;padding:4px 12px;background-color:#DCFCE7;color:#166534;font-size:12px;font-weight:700;border-radius:20px;margin-bottom:12px;\">&#10003; Booking Confirmed</div>" +
                "<h1 style=\"margin:0 0 8px 0;font-size:20px;font-weight:700;color:#0F172A;\">" + escape(subject) + "</h1>" +
                "<p style=\"margin:0 0 20px 0;font-size:14px;color:#64748B;\">Booking Reference: <strong style=\"color:#0B74DE;\">" + escape(bookingRef) + "</strong></p>" +
                "<table role=\"presentation\" width=\"100%\" style=\"background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;margin-bottom:20px;\">" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Route:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(origin) + " &rarr; " + escape(destination) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Date & Time:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(journeyDate) + " " + escape(departureTime) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Seats:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0B74DE;text-align:right;\">" + escape(seats) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Operator:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(operator) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Total Paid:</td><td style=\"padding:6px 0;font-size:16px;font-weight:700;color:#166534;text-align:right;\">&#8377;" + escape(totalFare) + "</td></tr>" +
                "</table>" +
                "<p style=\"font-size:13px;color:#64748B;line-height:1.5;\">Please arrive at your boarding location at least 15 minutes before departure. Have a safe and pleasant journey!</p>" +
                getFooterHtml();
    }

    private String renderBusBookingCancelledHtml(String subject, Map<String, Object> data) {
        String bookingRef = getVal(data, "bookingRef");
        String origin = getVal(data, "origin");
        String destination = getVal(data, "destination");
        String seats = getVal(data, "seats");
        String refundAmount = getVal(data, "refundAmount");

        return getHeaderHtml() +
                "<div style=\"display:inline-block;padding:4px 12px;background-color:#FEE2E2;color:#991B1B;font-size:12px;font-weight:700;border-radius:20px;margin-bottom:12px;\">&#10007; Booking Cancelled</div>" +
                "<h1 style=\"margin:0 0 8px 0;font-size:20px;font-weight:700;color:#0F172A;\">" + escape(subject) + "</h1>" +
                "<p style=\"margin:0 0 20px 0;font-size:14px;color:#64748B;\">Booking Reference: <strong style=\"color:#0F172A;\">" + escape(bookingRef) + "</strong></p>" +
                "<table role=\"presentation\" width=\"100%\" style=\"background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;margin-bottom:20px;\">" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Route:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(origin) + " &rarr; " + escape(destination) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Released Seats:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#991B1B;text-align:right;\">" + escape(seats) + "</td></tr>" +
                (refundAmount.isEmpty() ? "" : "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Refund Amount:</td><td style=\"padding:6px 0;font-size:14px;font-weight:700;color:#166534;text-align:right;\">&#8377;" + escape(refundAmount) + "</td></tr>") +
                "</table>" +
                "<p style=\"font-size:13px;color:#64748B;line-height:1.5;\">Your booking has been cancelled and the reserved seats released. If eligible for a refund, processing will complete shortly.</p>" +
                getFooterHtml();
    }

    private String renderPaymentFailedHtml(String subject, Map<String, Object> data) {
        String bookingRef = getVal(data, "bookingRef");
        String amount = getVal(data, "amount");
        String reason = getVal(data, "reason");

        return getHeaderHtml() +
                "<div style=\"display:inline-block;padding:4px 12px;background-color:#FEE2E2;color:#991B1B;font-size:12px;font-weight:700;border-radius:20px;margin-bottom:12px;\">&#9888; Payment Failed</div>" +
                "<h1 style=\"margin:0 0 8px 0;font-size:20px;font-weight:700;color:#0F172A;\">" + escape(subject) + "</h1>" +
                "<p style=\"margin:0 0 20px 0;font-size:14px;color:#64748B;\">Booking Reference: <strong>" + escape(bookingRef) + "</strong></p>" +
                "<table role=\"presentation\" width=\"100%\" style=\"background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;margin-bottom:20px;\">" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Amount:</td><td style=\"padding:6px 0;font-size:14px;font-weight:700;color:#0F172A;text-align:right;\">&#8377;" + escape(amount) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Failure Reason:</td><td style=\"padding:6px 0;font-size:13px;color:#DC2626;text-align:right;\">" + escape(reason.isEmpty() ? "Transaction declined by bank/gateway" : reason) + "</td></tr>" +
                "</table>" +
                "<p style=\"font-size:13px;color:#64748B;line-height:1.5;\">No charges were finalized. Please check your payment details or try an alternate payment method.</p>" +
                getFooterHtml();
    }

    private String renderRefundInitiatedHtml(String subject, Map<String, Object> data) {
        String bookingRef = getVal(data, "bookingRef");
        String refundId = getVal(data, "refundId");
        String amount = getVal(data, "refundAmount");

        return getHeaderHtml() +
                "<div style=\"display:inline-block;padding:4px 12px;background-color:#FEF3C7;color:#92400E;font-size:12px;font-weight:700;border-radius:20px;margin-bottom:12px;\">&#8635; Refund Initiated</div>" +
                "<h1 style=\"margin:0 0 8px 0;font-size:20px;font-weight:700;color:#0F172A;\">" + escape(subject) + "</h1>" +
                "<table role=\"presentation\" width=\"100%\" style=\"background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;margin-bottom:20px;\">" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Booking Ref:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(bookingRef) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Refund ID:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(refundId) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Refund Amount:</td><td style=\"padding:6px 0;font-size:15px;font-weight:700;color:#166534;text-align:right;\">&#8377;" + escape(amount) + "</td></tr>" +
                "</table>" +
                "<p style=\"font-size:13px;color:#64748B;line-height:1.5;\">Your refund request has been initiated and submitted to our payment processor. Funds will reflect in your original payment method in 3&ndash;5 business days.</p>" +
                getFooterHtml();
    }

    private String renderRefundCompletedHtml(String subject, Map<String, Object> data) {
        String bookingRef = getVal(data, "bookingRef");
        String refundId = getVal(data, "refundId");
        String amount = getVal(data, "refundAmount");

        return getHeaderHtml() +
                "<div style=\"display:inline-block;padding:4px 12px;background-color:#DCFCE7;color:#166534;font-size:12px;font-weight:700;border-radius:20px;margin-bottom:12px;\">&#10003; Refund Completed</div>" +
                "<h1 style=\"margin:0 0 8px 0;font-size:20px;font-weight:700;color:#0F172A;\">" + escape(subject) + "</h1>" +
                "<table role=\"presentation\" width=\"100%\" style=\"background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;margin-bottom:20px;\">" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Booking Ref:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(bookingRef) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Refund ID:</td><td style=\"padding:6px 0;font-size:14px;font-weight:600;color:#0F172A;text-align:right;\">" + escape(refundId) + "</td></tr>" +
                "<tr><td style=\"padding:6px 0;font-size:13px;color:#64748B;\">Settled Amount:</td><td style=\"padding:6px 0;font-size:15px;font-weight:700;color:#166534;text-align:right;\">&#8377;" + escape(amount) + "</td></tr>" +
                "</table>" +
                "<p style=\"font-size:13px;color:#64748B;line-height:1.5;\">Your refund has been settled successfully to your original payment method. Thank you for using Travel Easy.</p>" +
                getFooterHtml();
    }

    private String renderDefaultHtml(String subject, Map<String, Object> data) {
        String summary = getVal(data, "summary");
        if (summary.isEmpty()) summary = getVal(data, "message");

        return getHeaderHtml() +
                "<h1 style=\"margin:0 0 12px 0;font-size:20px;font-weight:700;color:#0F172A;\">" + escape(subject) + "</h1>" +
                "<p style=\"font-size:14px;color:#334155;line-height:1.6;margin-bottom:20px;\">" + escape(summary) + "</p>" +
                getFooterHtml();
    }

    private String getVal(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key) || data.get(key) == null) {
            return "";
        }
        return String.valueOf(data.get(key)).trim();
    }

    private String escape(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
