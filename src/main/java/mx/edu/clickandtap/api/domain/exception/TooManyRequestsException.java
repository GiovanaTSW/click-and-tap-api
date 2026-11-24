package mx.edu.clickandtap.api.domain.exception;

/** Too many attempts in a short time (HTTP 429). The client should wait {@code retryAfterSeconds}. */
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
