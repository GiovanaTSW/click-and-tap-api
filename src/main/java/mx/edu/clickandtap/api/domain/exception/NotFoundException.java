package mx.edu.clickandtap.api.domain.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}