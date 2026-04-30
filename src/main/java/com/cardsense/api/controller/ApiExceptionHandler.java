package com.cardsense.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_REQUEST",
                "message", "Recommendation request contains invalid values.",
                "fields", fields
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INVALID_JSON",
                "message", "Request body could not be parsed."
        ));
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage()
        );
    }
}
