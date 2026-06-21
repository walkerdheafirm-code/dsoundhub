package com.dsoundhub.audio_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendWithdrawalNotification(String to, String username, BigDecimal amount) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("D'SoundHub - Penarikan Saldo Berhasil");

            String html = buildWithdrawalHtml(username, amount);
            helper.setText(html, true);

            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send withdrawal email: " + e.getMessage());
        }
    }

    private String buildWithdrawalHtml(String username, BigDecimal amount) {
        String prefix = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; background: #f4f4f4; padding: 40px 20px;">
                <div style="max-width: 480px; margin: 0 auto; background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 20px rgba(0,0,0,0.08);">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="width: 48px; height: 48px; background: linear-gradient(135deg, #8f44fd, #00f2fe); border-radius: 14px; display: inline-flex; align-items: center; justify-content: center; color: #000; font-weight: 800; font-size: 1.2rem;">D</div>
                        <h2 style="color: #111; margin-top: 12px; font-size: 1.4rem;">D'SoundHub</h2>
                    </div>
                    <h3 style="color: #333; text-align: center; margin-bottom: 8px;">Penarikan Saldo Berhasil</h3>
                    <p style="color: #666; text-align: center; font-size: 0.95rem; line-height: 1.6;">
                        Halo <strong>""" + username + """
                    </strong>,
                    </p>
                    <p style="color: #666; text-align: center; font-size: 0.95rem; line-height: 1.6;">
                        Penarikan saldo sebesar <strong style="color: #8f44fd; font-size: 1.2rem;">""" + amount.toPlainString() + """
                        pts</strong> telah berhasil diproses.
                    </p>
                    <p style="color: #666; text-align: center; font-size: 0.95rem; line-height: 1.6;">
                        Saldo Anda telah dikurangi sesuai jumlah penarikan.
                    </p>
                    <p style="color: #999; text-align: center; font-size: 0.8rem; margin-top: 24px;">
                        Jika Anda tidak melakukan penarikan ini, segera hubungi admin D'SoundHub.
                    </p>
                </div>
            </body>
            </html>
            """;
        return prefix;
    }
}
