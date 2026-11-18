/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.mail;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Sends plain-text mail over SMTP — the flamelens donor ported as-is (itself the
 * taxdocscanner/warehouseorganizer pattern). First consumer here: pin-credit purchase receipts.
 *
 * <p>Sends run on the {@code mailTaskExecutor} pool, so a slow or wedged SMTP server can't hold up
 * the request thread. The trade-off is that the caller cannot learn whether delivery succeeded,
 * which is why {@link #isConfigured()} exists: callers check up front whether mail can go out at
 * all, and the outcome of an actual attempt is only ever visible in the log. Unconfigured SMTP is
 * a silent no-op — the launch-fast posture.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailProperties mailProperties;

    public MailService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    /**
     * Whether there is anywhere to send mail. Checked synchronously by callers before they promise
     * the user an email, since {@link #send} itself is fire-and-forget.
     */
    public boolean isConfigured() {
        return isPresent(mailProperties.host())
                && isPresent(mailProperties.notificationEmail())
                && isPresent(mailProperties.notificationPassword());
    }

    @Async("mailTaskExecutor")
    public void send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.warn("no SMTP configured (app.mail.*); not sending '{}'", subject);
            return;
        }
        try {
            Message message = new MimeMessage(buildSession());
            message.setFrom(new InternetAddress(mailProperties.notificationEmail()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            log.info("sent '{}' to {}", subject, to);
        } catch (MessagingException | RuntimeException e) {
            // Nothing upstream can react — this thread is detached from the request — so the log is
            // the only signal that a user is waiting on an email that never arrived.
            log.error("failed sending '{}' to {}", subject, to, e);
        }
    }

    /**
     * Plain-text mail with one file attached — the signed contract PDF that goes to both parties.
     * Same fire-and-forget contract as {@link #send}: check {@link #isConfigured()} first if the
     * caller needs to tell someone whether a copy went out.
     */
    @Async("mailTaskExecutor")
    public void sendWithAttachment(String to, String subject, String body,
                                   String filename, byte[] bytes, String contentType) {
        if (!isConfigured()) {
            log.warn("no SMTP configured (app.mail.*); not sending '{}' with {}", subject, filename);
            return;
        }
        try {
            MimeMessage message = new MimeMessage(buildSession());
            message.setFrom(new InternetAddress(mailProperties.notificationEmail()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(multipart(body, filename, bytes, contentType));
            Transport.send(message);
            log.info("sent '{}' with {} to {}", subject, filename, to);
        } catch (MessagingException | RuntimeException e) {
            log.error("failed sending '{}' with {} to {}", subject, filename, to, e);
        }
    }

    /** Text part + one attachment. Package-private so a test can inspect the structure. */
    static jakarta.mail.internet.MimeMultipart multipart(String body, String filename, byte[] bytes,
                                                         String contentType) throws MessagingException {
        jakarta.mail.internet.MimeBodyPart text = new jakarta.mail.internet.MimeBodyPart();
        text.setText(body, "UTF-8");
        jakarta.mail.internet.MimeBodyPart file = new jakarta.mail.internet.MimeBodyPart();
        file.setDataHandler(new jakarta.activation.DataHandler(
                new jakarta.mail.util.ByteArrayDataSource(bytes, contentType)));
        file.setFileName(filename);
        file.setDisposition(jakarta.mail.Part.ATTACHMENT);
        jakarta.mail.internet.MimeMultipart multipart = new jakarta.mail.internet.MimeMultipart();
        multipart.addBodyPart(text);
        multipart.addBodyPart(file);
        return multipart;
    }

    private Session buildSession() {
        Properties prop = new Properties();
        prop.put("mail.smtp.host", mailProperties.host());
        prop.put("mail.smtp.port", mailProperties.port());
        prop.put("mail.smtp.auth", mailProperties.auth());
        prop.put("mail.smtp.starttls.enable", mailProperties.starttlsEnabled());
        // Without timeouts a black-holed SMTP host ties up a pool thread forever.
        prop.put("mail.smtp.connectiontimeout", "5000");
        prop.put("mail.smtp.timeout", "5000");
        prop.put("mail.smtp.writetimeout", "5000");

        final String username = mailProperties.resolvedUsername();
        final String appPassword = mailProperties.notificationPassword();

        return Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, appPassword);
            }
        });
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
