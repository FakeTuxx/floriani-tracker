package floriani_tracker.controller;

import floriani_tracker.security.NotAuthenticatedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<Map<String, String>> notAuthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Bitte zuerst anmelden."));
    }
}
