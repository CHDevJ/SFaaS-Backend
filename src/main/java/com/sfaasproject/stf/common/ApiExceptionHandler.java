package com.sfaasproject.stf.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, Object>> domain(DomainException ex) {
        return ResponseEntity.status(ex.status())
                .body(
                        Map.of(
                                "success",
                                false,
                                "error",
                                Map.of("code", ex.code(), "message", ex.getMessage())));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        jakarta.validation.ConstraintViolationException.class
    })
    ResponseEntity<Map<String, Object>> validation(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "success",
                                false,
                                "error",
                                Map.of("code", "VALIDATION_ERROR", "message", "요청 값을 확인해 주세요.")));
    }
}
