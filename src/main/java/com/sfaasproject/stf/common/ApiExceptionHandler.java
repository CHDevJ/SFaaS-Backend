package com.sfaasproject.stf.common;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 컨트롤러의 예외를 {@code {success:false, error:{code, message}}} 형식으로 바꾼다.
 * 요청 본문, 비밀번호, 토큰은 응답과 로그에 남기지 않는다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, Object>> domain(DomainException ex) {
        return body(ex.status(), ex.code(), ex.getMessage(), null);
    }

    /**
     * 컨트롤러 밖에서 던져지는 입력 오류. 메서드 파라미터 검증(@Validated)의 ConstraintViolationException 을
     * 빠뜨리면 아래 500 처리에 걸려 서버 오류로 응답되므로 반드시 여기서 400 으로 바꾼다.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
            ConstraintViolationException.class})
    ResponseEntity<Map<String, Object>> validation(Exception ex) {
        return validationBody();
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "AUTH_PERMISSION_DENIED", "접근 권한이 없습니다.", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> authentication(AuthenticationException ex) {
        return body(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "다시 로그인해 주세요.", null);
    }

    /** 예상하지 못한 오류. 원인은 추적 번호와 함께 로그에만 남기고 응답에는 번호만 준다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception. traceId={}", traceId, ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", traceId);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(validationMap());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(validationMap());
    }

    /** 컨트롤러 메서드 파라미터(@RequestParam, @PathVariable 등)의 제약 위반. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.badRequest().body(validationMap());
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.badRequest().body(validationMap());
    }

    private ResponseEntity<Map<String, Object>> validationBody() {
        return ResponseEntity.badRequest().body(validationMap());
    }

    private Map<String, Object> validationMap() {
        return payload("VALIDATION_ERROR", "요청 값을 확인해 주세요.", null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message,
            String traceId) {
        return ResponseEntity.status(status).body(payload(code, message, traceId));
    }

    private Map<String, Object> payload(String code, String message, String traceId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (traceId != null) {
            error.put("traceId", traceId);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", false);
        root.put("error", error);
        return root;
    }
}
