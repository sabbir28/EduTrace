package org.sabbir.edutrace.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Manages SMTP email dispatch and One-Time Password (OTP) verification.
 * Sensitive SMTP credentials are compile-time obfuscated to prevent extraction.
 */
public class EmailManager {

    // Compile-time obfuscated credentials
    // smtp.gmail.com
    private static final byte[] ENC_SMTP_HOST = new byte[] { (byte) 0x14, (byte) 0x03, (byte) 0x01, (byte) 0x0C, (byte) 0xAD, (byte) 0xED, (byte) 0xFC, (byte) 0xF9, (byte) 0xF6, (byte) 0xCA, (byte) 0x83, (byte) 0xD7, (byte) 0xD4, (byte) 0xAF };
    // sabbirb228@gmail.com
    private static final byte[] ENC_SMTP_USER = new byte[] { (byte) 0x14, (byte) 0x0F, (byte) 0x17, (byte) 0x1E, (byte) 0xEA, (byte) 0xF8, (byte) 0xF3, (byte) 0xAA, (byte) 0xAD, (byte) 0x9E, (byte) 0xED, (byte) 0xD3, (byte) 0xD6, (byte) 0xA3, (byte) 0xA0, (byte) 0xBC, (byte) 0xF9, (byte) 0xBD, (byte) 0x8A, (byte) 0x81 };
    // ebbaspcnejmtldjb
    private static final byte[] ENC_SMTP_PASS = new byte[] { (byte) 0x02, (byte) 0x0C, (byte) 0x17, (byte) 0x1D, (byte) 0xF0, (byte) 0xFA, (byte) 0xF2, (byte) 0xF6, (byte) 0xFA, (byte) 0xCC, (byte) 0xC0, (byte) 0xC0, (byte) 0xD7, (byte) 0xA6, (byte) 0xA3, (byte) 0xB2 };
    // ca@casabbir.pro.bd
    private static final byte[] ENC_SMTP_FROM = new byte[] { (byte) 0x04, (byte) 0x0F, (byte) 0x35, (byte) 0x1F, (byte) 0xE2, (byte) 0xF9, (byte) 0xF0, (byte) 0xFA, (byte) 0xFD, (byte) 0xCF, (byte) 0xDF, (byte) 0x9A, (byte) 0xCB, (byte) 0xB0, (byte) 0xA6, (byte) 0xFE, (byte) 0xB5, (byte) 0xBA };
    // EduTrace Cloud
    private static final byte[] ENC_SMTP_NAME = new byte[] { (byte) 0x22, (byte) 0x0A, (byte) 0x00, (byte) 0x28, (byte) 0xF1, (byte) 0xEB, (byte) 0xF2, (byte) 0xFD, (byte) 0xBF, (byte) 0xE5, (byte) 0xC1, (byte) 0xDB, (byte) 0xCE, (byte) 0xA6 };

    private static final int SMTP_SSL_PORT = 465;
    private static final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static class OtpEntry {
        String code;
        long expiryTime;

        OtpEntry(String code, long expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }

    private static final Map<String, OtpEntry> activeOtps = new ConcurrentHashMap<>();

    public interface EmailCallback {
        void onSuccess();
        void onError(String error);
    }

    private static String decode(byte[] data) {
        byte key = 0x5A;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ ((key + (i * 7 + 13)) & 0xFF));
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    /**
     * Generates and stores a 6-digit OTP for a given email address valid for 10 minutes.
     */
    public static String generateOtp(String email) {
        if (email == null) return null;
        SecureRandom random = new SecureRandom();
        int num = 100000 + random.nextInt(900000);
        String code = String.valueOf(num);
        long expiry = System.currentTimeMillis() + (10 * 60 * 1000); // 10 minutes
        activeOtps.put(email.trim().toLowerCase(), new OtpEntry(code, expiry));
        return code;
    }

    /**
     * Validates whether an OTP matches and has not expired.
     */
    public static boolean verifyOtp(String email, String inputCode) {
        if (email == null || inputCode == null) return false;
        String cleanEmail = email.trim().toLowerCase();
        OtpEntry entry = activeOtps.get(cleanEmail);
        if (entry == null) return false;

        if (System.currentTimeMillis() > entry.expiryTime) {
            activeOtps.remove(cleanEmail);
            return false;
        }

        if (entry.code.equals(inputCode.trim())) {
            activeOtps.remove(cleanEmail);
            return true;
        }
        return false;
    }

    /**
     * Sends an OTP verification email formatted with a premium EduTrace HTML template.
     */
    public static void sendOtpEmail(String toEmail, String username, String otpCode, String purpose, EmailCallback callback) {
        String subject = "EduTrace Cloud Verification Code: " + otpCode;
        String htmlBody = "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'></head>" +
                "<body style='font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif; background-color:#0F172A; color:#FFFFFF; padding:24px; margin:0;'>" +
                "<div style='max-width:540px; margin:0 auto; background-color:#1E293B; border-radius:16px; padding:32px; border:1px solid #334155;'>" +
                "<div style='text-align:center; margin-bottom:24px;'>" +
                "<h1 style='color:#FACC15; margin:0; font-size:24px; letter-spacing:0.5px;'>EduTrace Cloud</h1>" +
                "<p style='color:#94A3B8; font-size:13px; margin:4px 0 0 0;'>Study Intelligence &amp; Cloud Sync</p>" +
                "</div>" +
                "<h2 style='font-size:18px; color:#FFFFFF; margin-bottom:12px;'>Hello " + (username != null ? username : "Learner") + ",</h2>" +
                "<p style='color:#CBD5E1; font-size:14px; line-height:1.6; margin-bottom:24px;'>" +
                "Use the following One-Time Password (OTP) to complete your <strong>" + purpose + "</strong>. This code is confidential and will expire in 10 minutes.</p>" +
                "<div style='text-align:center; margin:28px 0;'>" +
                "<span style='display:inline-block; font-size:32px; font-weight:bold; letter-spacing:6px; color:#0F172A; background-color:#FACC15; padding:12px 28px; border-radius:12px; font-family:Courier,monospace;'>" +
                otpCode + "</span>" +
                "</div>" +
                "<p style='color:#94A3B8; font-size:12px; line-height:1.5; margin-top:24px;'>" +
                "If you did not request this verification code, please ignore this email. No changes will be made to your account." +
                "</p>" +
                "<hr style='border:none; border-top:1px solid #334155; margin:24px 0;'>" +
                "<p style='color:#64748B; font-size:11px; text-align:center; margin:0;'>" +
                "EduTrace Intelligence • Secure Cloud Backup • 2026" +
                "</p>" +
                "</div>" +
                "</body></html>";

        sendEmail(toEmail, subject, htmlBody, callback);
    }

    /**
     * Sends an email via pure Java SSL socket SMTP (Port 465).
     */
    public static void sendEmail(String toEmail, String subject, String htmlBody, EmailCallback callback) {
        emailExecutor.execute(() -> {
            SSLSocket socket = null;
            BufferedReader reader = null;
            BufferedWriter writer = null;

            try {
                String host = decode(ENC_SMTP_HOST);
                String user = decode(ENC_SMTP_USER);
                String pass = decode(ENC_SMTP_PASS);
                String from = decode(ENC_SMTP_FROM);
                String name = decode(ENC_SMTP_NAME);

                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                socket = (SSLSocket) factory.createSocket(host, SMTP_SSL_PORT);
                socket.setSoTimeout(12000);

                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                readResponse(reader, "220"); // 220 banner

                sendCommand(writer, "EHLO localhost");
                readMultiLineResponse(reader, "250");

                sendCommand(writer, "AUTH LOGIN");
                readResponse(reader, "334"); // 334 VXNlcm5hbWU6

                String b64User = Base64.encodeToString(user.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                sendCommand(writer, b64User);
                readResponse(reader, "334"); // 334 UGFzc3dvcmQ6

                String b64Pass = Base64.encodeToString(pass.replace(" ", "").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                sendCommand(writer, b64Pass);
                readResponse(reader, "235"); // 235 Authentication successful

                sendCommand(writer, "MAIL FROM:<" + user + ">");
                readResponse(reader, "250");

                sendCommand(writer, "RCPT TO:<" + toEmail.trim() + ">");
                readResponse(reader, "250");

                sendCommand(writer, "DATA");
                readResponse(reader, "354");

                // Headers & MIME Body
                writer.write("From: \"" + name + "\" <" + from + ">\r\n");
                writer.write("To: <" + toEmail.trim() + ">\r\n");
                writer.write("Subject: =?UTF-8?B?" + Base64.encodeToString(subject.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP) + "?=\r\n");
                writer.write("MIME-Version: 1.0\r\n");
                writer.write("Content-Type: text/html; charset=UTF-8\r\n");
                writer.write("Content-Transfer-Encoding: base64\r\n");
                writer.write("\r\n");

                String b64Body = Base64.encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
                writer.write(b64Body);
                writer.write("\r\n.\r\n");
                writer.flush();

                readResponse(reader, "250"); // 250 Message accepted for delivery

                sendCommand(writer, "QUIT");
                try { readResponse(reader, "221"); } catch (Exception ignored) {}

                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    String err = e.getMessage() != null ? e.getMessage() : "SMTP connection failed";
                    mainHandler.post(() -> callback.onError(err));
                }
            } finally {
                try { if (writer != null) writer.close(); } catch (Exception ignored) {}
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
        });
    }

    private static void sendCommand(BufferedWriter writer, String cmd) throws Exception {
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private static String readResponse(BufferedReader reader, String expectedCode) throws Exception {
        String line = reader.readLine();
        if (line == null || !line.startsWith(expectedCode)) {
            throw new Exception("SMTP Error: " + (line != null ? line : "No response from server"));
        }
        return line;
    }

    private static void readMultiLineResponse(BufferedReader reader, String expectedCode) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(expectedCode + " ")) {
                return; // Final line
            }
            if (!line.startsWith(expectedCode + "-")) {
                throw new Exception("Unexpected SMTP response: " + line);
            }
        }
    }
}
