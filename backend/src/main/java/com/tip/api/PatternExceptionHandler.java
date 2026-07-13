package com.tip.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PatternExceptionHandler {

    @ExceptionHandler(PatternsDisabledException.class)
    public ResponseEntity<Map<String, String>> patternsDisabled(PatternsDisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "patterns_disabled"));
    }
}
