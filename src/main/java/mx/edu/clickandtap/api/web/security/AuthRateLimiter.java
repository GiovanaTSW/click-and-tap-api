package mx.edu.clickandtap.api.web.security;

import mx.edu.clickandtap.api.domain.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory brute-force protection for login and registration (sliding window of recent events).
 *
 * Why in memory: the API runs as a single instance, so a shared store (Redis) would be extra
 * infrastructure for no benefit. Limits reset on restart and are not shared between instances; if the
 * API is ever scaled horizontally, move this to Redis or to the reverse proxy.
 *
 * Two counters protect login:
 *  - per (IP + email): 5 failures in 15 minutes blocks that combination, which stops password guessing
 *    against one account without letting an attacker lock the real owner out from other networks;
 *  - per IP: 20 failures in 15 minutes blocks that IP, which stops one machine from trying many accounts.
 */
@Component
public class AuthRateLimiter {
    private static final int MAX_FAILURES_PER_ACCOUNT = 5;
    private static final int MAX_FAILURES_PER_IP = 20;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);

    private static final int MAX_REGISTRATIONS_PER_IP = 10;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    /** Safety valve so a flood of different keys cannot grow the map without limit. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Long>> events = new HashMap<>();
    private final Clock clock;

    public AuthRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Visible for tests, which pass a fixed clock. */
    AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Call before checking credentials. Throws if this IP or this IP+email is currently blocked. */
    public synchronized void checkLogin(String ip, String email) {
        long now = clock.millis();
        failIfBlocked(accountKey(ip, email), MAX_FAILURES_PER_ACCOUNT, LOGIN_WINDOW, now);
        failIfBlocked(ipKey(ip), MAX_FAILURES_PER_IP, LOGIN_WINDOW, now);
    }

    public synchronized void recordLoginFailure(String ip, String email) {
        long now = clock.millis();
        evictIfTooBig(now);
        events.computeIfAbsent(accountKey(ip, email), k -> new ArrayDeque<>()).addLast(now);
        events.computeIfAbsent(ipKey(ip), k -> new ArrayDeque<>()).addLast(now);
    }

    /** A successful login clears the failures of that account (the per-IP counter keeps counting). */
    public synchronized void recordLoginSuccess(String ip, String email) {
        events.remove(accountKey(ip, email));
    }

    /** Counts a registration attempt and throws if this IP has registered too many accounts recently. */
    public synchronized void checkAndRecordRegistration(String ip) {
        long now = clock.millis();
        String key = "register:" + ip;
        failIfBlocked(key, MAX_REGISTRATIONS_PER_IP, REGISTER_WINDOW, now);
        evictIfTooBig(now);
        events.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(now);
    }

    private void failIfBlocked(String key, int max, Duration window, long now) {
        Deque<Long> recent = events.get(key);
        if (recent == null) return;
        long windowMs = window.toMillis();
        while (!recent.isEmpty() && now - recent.peekFirst() >= windowMs) recent.removeFirst();
        if (recent.isEmpty()) {
            events.remove(key);
            return;
        }
        if (recent.size() >= max) {
            long retryAfterMs = windowMs - (now - recent.peekFirst());
            long retryAfterSeconds = Math.max(1, (retryAfterMs + 999) / 1000);
            throw new TooManyRequestsException("Too many attempts. Try again later", retryAfterSeconds);
        }
    }

    private void evictIfTooBig(long now) {
        if (events.size() < MAX_TRACKED_KEYS) return;
        long longestWindowMs = REGISTER_WINDOW.toMillis();
        events.values().removeIf(q -> q.isEmpty() || now - q.peekLast() >= longestWindowMs);
    }

    private String accountKey(String ip, String email) {
        return "login:acct:" + ip + "|" + email.trim().toLowerCase(Locale.ROOT);
    }

    private String ipKey(String ip) {
        return "login:ip:" + ip;
    }
}
