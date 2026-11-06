package org.sabbir.edutrace.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Patterns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Advanced Email Domain & DNS Verification Technology.
 * Validates email addresses by querying global DNS systems (DNS-over-HTTPS with Google & Cloudflare DNS,
 * plus native InetAddress fallback) to ensure the domain actually exists and accepts mail (MX records).
 * Also filters out disposable / temporary email services.
 */
public class EmailDnsValidator {

    private static final String TAG = "EmailDnsValidator";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
    );

    // Common disposable / burner email domains
    private static final Set<String> DISPOSABLE_DOMAINS = new HashSet<>(Arrays.asList(
            "mailinator.com", "10minutemail.com", "guerrillamail.com", "tempmail.com", "temp-mail.org",
            "throwawaymail.com", "yopmail.com", "trashmail.com", "sharklasers.com", "getairmail.com",
            "dispostable.com", "fakeinbox.com", "mohmal.com", "nada.ltd", "crazymailing.com",
            "burnermail.io", "generator.email", "inboxkitten.com", "mytemp.email", "tempail.com",
            "fakemailgenerator.com", "emailondeck.com", "mailnesia.com", "maildrop.cc", "dropmail.me"
    ));

    // In-memory cache for validated domains (domain -> ValidationResult)
    private static final Map<String, DnsResult> DOMAIN_CACHE = new ConcurrentHashMap<>();

    public static class DnsResult {
        public final boolean isValid;
        public final String domain;
        public final String mailServer;
        public final String errorMessage;

        public DnsResult(boolean isValid, String domain, String mailServer, String errorMessage) {
            this.isValid = isValid;
            this.domain = domain;
            this.mailServer = mailServer;
            this.errorMessage = errorMessage;
        }

        public static DnsResult success(String domain, String mailServer) {
            return new DnsResult(true, domain, mailServer, null);
        }

        public static DnsResult failure(String domain, String error) {
            return new DnsResult(false, domain, null, error);
        }
    }

    public interface DnsCallback {
        void onVerified(String email, String domain, String mailServer);
        void onError(String email, String domain, String reason);
    }

    /**
     * Synchronously validates email syntax, disposable status, and DNS MX/A records.
     * Note: Must be executed on a background thread.
     */
    public static DnsResult validateEmailSync(String email) {
        if (email == null || email.trim().isEmpty()) {
            return DnsResult.failure("", "Email address cannot be empty.");
        }

        String cleanEmail = email.trim();

        // 1. Syntax Format Check
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return DnsResult.failure("", "Invalid email format. Please check the spelling.");
        }

        int atIndex = cleanEmail.indexOf('@');
        if (atIndex <= 0 || atIndex >= cleanEmail.length() - 1) {
            return DnsResult.failure("", "Email must contain a username and a domain separated by '@'.");
        }

        String localPart = cleanEmail.substring(0, atIndex).trim();
        String domain = cleanEmail.substring(atIndex + 1).trim().toLowerCase(Locale.US);

        if (localPart.length() > 64) {
            return DnsResult.failure(domain, "Email username exceeds the 64-character limit.");
        }

        if (!DOMAIN_PATTERN.matcher(domain).matches() || !domain.contains(".")) {
            return DnsResult.failure(domain, "Domain '" + domain + "' is not a valid internet domain.");
        }

        int lastDot = domain.lastIndexOf('.');
        String tld = domain.substring(lastDot + 1);
        if (tld.length() < 2) {
            return DnsResult.failure(domain, "Top-level domain (." + tld + ") is invalid.");
        }

        // 2. Disposable / Burner Domain Blocklist
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            return DnsResult.failure(domain, "Temporary / disposable email domains (" + domain + ") are not permitted. Please use a permanent email.");
        }

        // 3. Fast Cache Lookup
        if (DOMAIN_CACHE.containsKey(domain)) {
            return DOMAIN_CACHE.get(domain);
        }

        // 4. DNS MX / A Record Resolution using DNS-over-HTTPS (DoH) & Native Fallback
        DnsResult dnsResult = verifyDomainDns(domain);
        if (dnsResult.isValid) {
            DOMAIN_CACHE.put(domain, dnsResult);
        }
        return dnsResult;
    }

    /**
     * Asynchronously validates email syntax, disposable status, and queries DNS.
     * Dispatches callback results to the Android Main Thread.
     */
    public static void validateEmailAsync(String email, DnsCallback callback) {
        executor.execute(() -> {
            DnsResult result = validateEmailSync(email);
            mainHandler.post(() -> {
                if (callback == null) return;
                if (result.isValid) {
                    callback.onVerified(email, result.domain, result.mailServer);
                } else {
                    callback.onError(email, result.domain, result.errorMessage);
                }
            });
        });
    }

    /**
     * Queries DNS using Google DoH, Cloudflare DoH, and native InetAddress fallback.
     */
    private static DnsResult verifyDomainDns(String domain) {
        // Attempt 1: Google DNS-over-HTTPS (RFC 8484) for MX records
        DnsResult dohGoogle = queryDnsOverHttps("https://dns.google/resolve?name=" + domain + "&type=MX", domain);
        if (dohGoogle != null) {
            return dohGoogle;
        }

        // Attempt 2: Cloudflare DNS-over-HTTPS for MX records
        DnsResult dohCloudflare = queryDnsOverHttps("https://cloudflare-dns.com/dns-query?name=" + domain + "&type=MX", domain);
        if (dohCloudflare != null) {
            return dohCloudflare;
        }

        // Attempt 3: Native DNS Resolution Fallback (InetAddress)
        try {
            InetAddress[] addresses = InetAddress.getAllByName(domain);
            if (addresses != null && addresses.length > 0) {
                Log.d(TAG, "Native DNS resolved " + domain + " -> " + addresses[0].getHostAddress());
                return DnsResult.success(domain, addresses[0].getHostAddress());
            }
        } catch (UnknownHostException uhe) {
            Log.w(TAG, "Native DNS UnknownHostException for " + domain);
            return DnsResult.failure(domain, "DNS verification failed: The domain '" + domain + "' does not exist or has no active mail server.");
        } catch (Throwable t) {
            Log.w(TAG, "Native DNS lookup error: " + t.getMessage());
        }

        // If network was completely offline during validation or timeouts:
        // Do a secondary check for host reachable or format validity
        return DnsResult.failure(domain, "Unable to verify mail server for '" + domain + "'. Please verify your internet connection or email spelling.");
    }

    /**
     * Executes a DNS-over-HTTPS query and parses standard DNS JSON response.
     */
    private static DnsResult queryDnsOverHttps(String endpoint, String domain) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/dns-json");
            conn.setRequestProperty("User-Agent", "EduTrace-DNS-Validator/1.0");
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(3500);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                int status = json.optInt("Status", -1);

                // DNS Status 3 = NXDOMAIN (Non-Existent Domain)
                if (status == 3) {
                    return DnsResult.failure(domain, "DNS check failed: Domain '" + domain + "' does not exist (NXDOMAIN).");
                }

                // DNS Status 0 = NOERROR (Domain exists)
                if (status == 0) {
                    JSONArray answers = json.optJSONArray("Answer");
                    if (answers != null && answers.length() > 0) {
                        for (int i = 0; i < answers.length(); i++) {
                            JSONObject ans = answers.getJSONObject(i);
                            int type = ans.optInt("type", -1);
                            // Type 15 = MX (Mail Exchange)
                            if (type == 15) {
                                String mxData = ans.optString("data", "");
                                return DnsResult.success(domain, mxData.isEmpty() ? domain : mxData);
                            }
                        }
                        // If no MX record in Answer, but has A/AAAA records (Type 1 or 28), RFC allows direct delivery
                        for (int i = 0; i < answers.length(); i++) {
                            JSONObject ans = answers.getJSONObject(i);
                            int type = ans.optInt("type", -1);
                            if (type == 1 || type == 28) {
                                return DnsResult.success(domain, ans.optString("data", domain));
                            }
                        }
                    } else {
                        // Domain exists (NOERROR) but has no MX record. Check if A record exists
                        return verifyDomainARecord(domain);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "DoH query failed on " + endpoint + ": " + t.getMessage());
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Checks if the domain has a valid A record when MX is missing (RFC 5321 fallback).
     */
    private static DnsResult verifyDomainARecord(String domain) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://dns.google/resolve?name=" + domain + "&type=A");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/dns-json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                int status = json.optInt("Status", -1);
                if (status == 3) {
                    return DnsResult.failure(domain, "DNS check failed: Domain '" + domain + "' does not exist.");
                }
                JSONArray answers = json.optJSONArray("Answer");
                if (answers != null && answers.length() > 0) {
                    return DnsResult.success(domain, "A-Record Fallback");
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return DnsResult.failure(domain, "DNS check failed: Domain '" + domain + "' has no registered mail server (MX record).");
    }
}

