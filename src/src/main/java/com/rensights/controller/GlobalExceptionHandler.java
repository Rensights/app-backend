package com.rensights.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle validation errors from @Valid annotated request bodies
     * Returns user-friendly error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        // Extract field errors and create friendly messages
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            
            // Make password errors more user-friendly
            if ("password".equals(fieldName) && errorMessage != null) {
                if (errorMessage.contains("Password must contain")) {
                    // Already a detailed message, use it as is
                    errors.put(fieldName, errorMessage);
                } else if (errorMessage.contains("between 8 and 128")) {
                    errors.put(fieldName, "Password must be at least 8 characters long");
                } else {
                    errors.put(fieldName, errorMessage);
                }
            } else {
                errors.put(fieldName, errorMessage != null ? errorMessage : "Invalid value");
            }
        });
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("errors", errors);
        
        // For password field, provide a helpful summary
        if (errors.containsKey("password")) {
            response.put("message", "Please fix the following errors: " + errors.get("password"));
        } else {
            response.put("message", "Please check your input and try again");
        }
        
        logger.warn("Validation error: {}", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
