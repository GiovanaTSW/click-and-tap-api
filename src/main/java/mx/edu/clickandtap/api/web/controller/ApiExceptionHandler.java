package mx.edu.clickandtap.api.web.controller;

import lombok.extern.slf4j.Slf4j;
import mx.edu.clickandtap.api.domain.exception.BadRequestException;
import mx.edu.clickandtap.api.domain.exception.ConflictException;
import mx.edu.clickandtap.api.domain.exception.ForbiddenException;
import mx.edu.clickandtap.api.domain.exception.NotFoundException;
import mx.edu.clickandtap.api.domain.exception.TooManyRequestsException;
import mx.edu.clickandtap.api.domain.exception.UnauthorizedException;
import mx.edu.clickandtap.api.web.dto.ApiDto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Every error leaves the API as {"status": ..., "message": ...}.
 * Extending ResponseEntityExceptionHandler covers Spring's own exceptions (405, 415, 404, bad
 * parameter types, ...) and handleExceptionInternal below converts them to our error shape.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) { return build(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException e) { return build(HttpStatus.BAD_REQUEST, e.getMessage()); }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(ForbiddenException e) { return build(HttpStatus.FORBIDDEN, e.getMessage()); }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException e) { return build(HttpStatus.UNAUTHORIZED, e.getMessage()); }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> tooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> stateConflict(ConflictException e) { return build(HttpStatus.CONFLICT, e.getMessage()); }

    /** Real conflicts (duplicate email, product still referenced by orders). Input sizes are validated in the DTOs. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException e) {
        log.debug("Data integrity violation", e);
        return build(HttpStatus.CONFLICT, "Operation conflicts with existing data");
    }

    /** Last resort: log the details on the server, never send them to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildRaw(status, message.isBlank() ? "Validation failed" : message, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        return buildRaw(status, "Malformed or invalid JSON request", headers);
    }

    /** Single exit point for all Spring MVC exceptions: replace ProblemDetail with our error shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : defaultMessage(statusCode);
        return buildRaw(statusCode, message, headers);
    }

    private String defaultMessage(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : "Request failed";
    }

    private ResponseEntity<Object> buildRaw(HttpStatusCode status, String message, HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).body(new ErrorResponse(status.value(), message));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
