package com.traveleasy.auth.service;

public class PasswordResetEmailTemplate {

    public static String buildHtml(String recipientName, String resetUrl, int expiryMinutes) {
        String safeName = escapeHtml(recipientName != null ? recipientName.trim() : "");
        String greeting = safeName.isEmpty() ? "Hello," : "Hello, " + safeName;
        String safeUrl = escapeHtml(resetUrl);

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Reset your Travel Easy password</title>\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 0; background-color: #F1F7FC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;\">\n" +
                "  <!-- Preheader -->\n" +
                "  <div style=\"display: none; font-size: 1px; color: #F1F7FC; line-height: 1px; max-height: 0; max-width: 0; opacity: 0; overflow: hidden;\">\n" +
                "    Use the secure link to reset your password. It expires in " + expiryMinutes + " minutes.\n" +
                "  </div>\n" +
                "  <table role=\"presentation\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color: #F1F7FC; padding: 40px 16px;\">\n" +
                "    <tr>\n" +
                "      <td align=\"center\">\n" +
                "        <table role=\"presentation\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width: 520px; background-color: #ffffff; border-radius: 16px; border: 1px solid #E2E8F0; box-shadow: 0 4px 18px rgba(15, 23, 42, 0.05); overflow: hidden;\">\n" +
                "          <!-- Card Header & Brand -->\n" +
                "          <tr>\n" +
                "            <td style=\"padding: 28px 32px 0 32px;\">\n" +
                "              <table role=\"presentation\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n" +
                "                <tr>\n" +
                "                  <td style=\"width: 40px; height: 40px; vertical-align: middle;\">\n" +
                "                    <img src=\"cid:travel-easy-logo\" alt=\"Travel Easy\" width=\"40\" height=\"40\" style=\"display: block; border-radius: 10px; border: 0;\" />\n" +
                "                  </td>\n" +
                "                  <td style=\"padding-left: 12px; vertical-align: middle;\">\n" +
                "                    <div style=\"font-size: 20px; font-weight: 700; color: #0F172A; line-height: 1.2;\">Travel <span style=\"color: #0B74DE;\">Easy</span></div>\n" +
                "                    <div style=\"font-size: 9.5px; font-weight: 700; color: #64748B; letter-spacing: 0.8px; text-transform: uppercase;\">Made Travel Easy</div>\n" +
                "                  </td>\n" +
                "                </tr>\n" +
                "              </table>\n" +
                "              <div style=\"height: 1px; background-color: #E2E8F0; margin-top: 20px;\"></div>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "\n" +
                "          <!-- Card Body -->\n" +
                "          <tr>\n" +
                "            <td style=\"padding: 28px 32px 32px 32px;\">\n" +
                "              <!-- Lock Icon Badge -->\n" +
                "              <div style=\"text-align: center; margin-bottom: 20px;\">\n" +
                "                <img src=\"cid:lock-icon\" alt=\"Reset Password\" width=\"64\" height=\"64\" style=\"display: inline-block; margin: 0 auto; border: 0;\" />\n" +
                "              </div>\n" +
                "\n" +
                "              <!-- Heading & Subtitle -->\n" +
                "              <h1 style=\"margin: 0 0 8px 0; font-size: 24px; font-weight: 700; color: #0F172A; text-align: center; letter-spacing: -0.3px;\">Reset Your Password</h1>\n" +
                "              <p style=\"margin: 0 0 24px 0; font-size: 14px; color: #64748B; text-align: center;\">Let's get you back to planning your next journey.</p>\n" +
                "\n" +
                "              <!-- Personalized Greeting & Instructions -->\n" +
                "              <p style=\"margin: 0 0 14px 0; font-size: 15px; color: #334155; line-height: 1.5;\">" + greeting + "</p>\n" +
                "              <p style=\"margin: 0 0 26px 0; font-size: 15px; color: #334155; line-height: 1.6;\">\n" +
                "                We received a request to reset the password for your Travel Easy account. Click the button below to choose a new password.\n" +
                "              </p>\n" +
                "\n" +
                "              <!-- Primary Action Button -->\n" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-bottom: 24px;\">\n" +
                "                <tr>\n" +
                "                  <td align=\"center\">\n" +
                "                    <a href=\"" + safeUrl + "\" target=\"_blank\" style=\"display: inline-block; background-color: #0B74DE; color: #ffffff; text-decoration: none; font-size: 15px; font-weight: 600; padding: 13px 36px; border-radius: 8px; box-shadow: 0 2px 6px rgba(11, 116, 222, 0.28); text-align: center;\">\n" +
                "                      Reset My Password\n" +
                "                    </a>\n" +
                "                  </td>\n" +
                "                </tr>\n" +
                "              </table>\n" +
                "\n" +
                "              <!-- Expiry Notice -->\n" +
                "              <p style=\"margin: 0 0 20px 0; font-size: 13.5px; color: #64748B; line-height: 1.5;\">\n" +
                "                For your security, this link expires in <strong>" + expiryMinutes + " minutes</strong> and can be used only once.\n" +
                "              </p>\n" +
                "\n" +
                "              <div style=\"height: 1px; background-color: #E2E8F0; margin: 20px 0;\"></div>\n" +
                "\n" +
                "              <!-- Fallback Link Section -->\n" +
                "              <p style=\"margin: 0 0 6px 0; font-size: 13.5px; color: #64748B; line-height: 1.5;\">\n" +
                "                If the button doesn't work, copy and paste this link into your browser:\n" +
                "              </p>\n" +
                "              <p style=\"margin: 0 0 22px 0; font-size: 13px; line-height: 1.5; word-break: break-all;\">\n" +
                "                <a href=\"" + safeUrl + "\" target=\"_blank\" style=\"color: #0B74DE; text-decoration: underline;\">" + safeUrl + "</a>\n" +
                "              </p>\n" +
                "\n" +
                "              <!-- Callout Notice Box -->\n" +
                "              <div style=\"background-color: #FFF7ED; border: 1px solid #FED7AA; border-radius: 10px; padding: 14px 18px; margin-bottom: 24px;\">\n" +
                "                <table role=\"presentation\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">\n" +
                "                  <tr>\n" +
                "                    <td style=\"width: 24px; vertical-align: top; padding-right: 12px;\">\n" +
                "                      <img src=\"cid:shield-icon\" alt=\"Notice\" width=\"24\" height=\"24\" style=\"display: block; border: 0;\" />\n" +
                "                    </td>\n" +
                "                    <td style=\"vertical-align: middle;\">\n" +
                "                      <div style=\"font-size: 13.5px; font-weight: 700; color: #9A3412; margin-bottom: 2px;\">Didn't request a password reset?</div>\n" +
                "                      <div style=\"font-size: 13px; color: #9A3412; line-height: 1.4;\">You can safely ignore this email. Your password will remain unchanged.</div>\n" +
                "                    </td>\n" +
                "                  </tr>\n" +
                "                </table>\n" +
                "              </div>\n" +
                "\n" +
                "              <!-- Sign-off -->\n" +
                "              <p style=\"margin: 0 0 4px 0; font-size: 14.5px; color: #334155;\">Safe travels,</p>\n" +
                "              <p style=\"margin: 0; font-size: 15px; font-weight: 700; color: #1E3A8A;\">Team, Travel Easy</p>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "\n" +
                "          <!-- Card Footer -->\n" +
                "          <tr>\n" +
                "            <td style=\"background-color: #F8FAFC; border-top: 1px solid #E2E8F0; padding: 20px 32px; text-align: center;\">\n" +
                "              <p style=\"margin: 0 0 6px 0; font-size: 12px; color: #94A3B8; line-height: 1.4;\">\n" +
                "                This is an automated security email from Travel Easy. Please do not reply to this message.\n" +
                "              </p>\n" +
                "              <p style=\"margin: 0; font-size: 12px; color: #94A3B8;\">\n" +
                "                &copy; Travel Easy\n" +
                "              </p>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </table>\n" +
                "</body>\n" +
                "</html>";
    }

    public static String buildPlainText(String recipientName, String resetUrl, int expiryMinutes) {
        String name = recipientName != null && !recipientName.trim().isEmpty() ? recipientName.trim() : "there";
        return "Hello " + name + ",\n\n" +
                "We received a request to reset the password for your Travel Easy account. Use the link below to choose a new password:\n\n" +
                resetUrl + "\n\n" +
                "For your security, this link expires in " + expiryMinutes + " minutes and can be used only once.\n\n" +
                "Didn't request a password reset? You can safely ignore this email. Your password will remain unchanged.\n\n" +
                "Safe travels,\n" +
                "The Travel Easy Team\n\n" +
                "---\n" +
                "This is an automated security email from Travel Easy. Please do not reply to this message.\n" +
                "© Travel Easy\n";
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
