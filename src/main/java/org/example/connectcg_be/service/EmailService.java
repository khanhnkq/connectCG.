package org.example.connectcg_be.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${mail.from.email:${spring.mail.username:noreply@connect.com}}")
    private String fromEmail;

    @Value("${mail.from.name:Connect App}")
    private String fromName;

    public void sendHtmlMessage(String to, String subject, String htmlBody) {
        logger.info("=== SENDING EMAIL VIA SPRING MAIL SERVICE ===");
        logger.info("To: {}", to);
        logger.info("From: {} <{}>", fromName, fromEmail);
        logger.info("Subject: {}", subject);

        if (mailSender == null) {
            logger.warn("⚠️ JavaMailSender is not configured. Email to {} was not sent via SMTP.", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String senderEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : mailUsername;
            if (senderEmail == null || senderEmail.isBlank()) {
                senderEmail = "noreply@connect.com";
            }

            helper.setFrom(senderEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML format

            mailSender.send(message);
            logger.info("✅ Email sent successfully to {}", to);
            logger.info("=== EMAIL SENT ===");
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("❌ Failed to send email to {}", to, e);
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage(), e);
        }
    }
}