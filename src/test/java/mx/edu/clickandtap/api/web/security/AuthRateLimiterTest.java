package mx.edu.clickandtap.api.web.security;

import mx.edu.clickandtap.api.domain.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {
    private static final String IP = "203.0.113.5";
    private static final String EMAIL = "ana@example.com";

    private MutableClock clock;
    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new AuthRateLimiter(clock);
    }

    @Test
    void fourFailuresAreStillAllowed() {
        failLogin(4, IP, EMAIL);

        assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void fifthFailureBlocksThatAccountFromThatIp() {
        failLogin(5, IP, EMAIL);

        assertThatThrownBy(() -> limiter.checkLogin(IP, EMAIL)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        failLogin(5, IP, EMAIL);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.checkLogin(IP, EMAIL))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(600)); // 15 min window - 5 min
    }

    @Test
    void blockLiftsAfterTheWindowPasses() {
        failLogin(5, IP, EMAIL);
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsTheAccountFailures() {
        failLogin(4, IP, EMAIL);
        limiter.recordLoginSuccess(IP, EMAIL);
        failLogin(4, IP, EMAIL);

        assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void blockingOneAccountDoesNotBlockOtherAccountsFromTheSameIp() {
        failLogin(5, IP, EMAIL);

        assertThatCode(() -> limiter.checkLogin(IP, "otra@example.com")).doesNotThrowAnyException();
    }

    @Test
    void blockingAnIpAndAccountDoesNotLockTheOwnerOutFromAnotherNetwork() {
        failLogin(5, IP, EMAIL);

        assertThatCode(() -> limiter.checkLogin("198.51.100.9", EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void emailsAreComparedIgnoringCaseAndSpaces() {
        failLogin(5, IP, "  ANA@Example.com ");

        assertThatThrownBy(() -> limiter.checkLogin(IP, EMAIL)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void oneIpTryingManyAccountsGetsBlockedAfterTwentyFailures() {
        for (int i = 0; i < 20; i++) {
            limiter.recordLoginFailure(IP, "user" + i + "@example.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin(IP, "brand-new@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void tenRegistrationsPerHourAreAllowedAndTheEleventhIsNot() {
        for (int i = 0; i < 10; i++) {
            limiter.checkAndRecordRegistration(IP);
        }

        assertThatThrownBy(() -> limiter.checkAndRecordRegistration(IP))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkAndRecordRegistration("198.51.100.9")).doesNotThrowAnyException();
    }

    @Test
    void registrationBlockLiftsAfterOneHour() {
        for (int i = 0; i < 10; i++) {
            limiter.checkAndRecordRegistration(IP);
        }
        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.checkAndRecordRegistration(IP)).doesNotThrowAnyException();
    }

    private void failLogin(int times, String ip, String email) {
        for (int i = 0; i < times; i++) {
            limiter.recordLoginFailure(ip, email);
        }
    }

    /** A clock the test can move forward, so no test has to sleep. */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
