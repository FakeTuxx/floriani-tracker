package floriani_tracker.controller;

import floriani_tracker.model.AppUser;
import floriani_tracker.repository.AppUserRepository;
import floriani_tracker.security.PasswordService;
import floriani_tracker.security.SessionUser;
import floriani_tracker.security.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordService passwordService = new PasswordService();

    public AuthController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<SessionUser> me(HttpSession session) {
        Object value = session.getAttribute(SessionUtil.SESSION_USER_KEY);
        if (value instanceof SessionUser sessionUser) {
            return ResponseEntity.ok(sessionUser);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Benutzername und Passwort sind erforderlich."));
        }

        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(AppUser::isActive)
                .orElse(null);

        if (user == null || !passwordService.verifyPassword(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Zugangsdaten stimmen nicht."));
        }

        if (user.getFireDepartment() != null && !user.getFireDepartment().hasValidSubscription()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Der Zugang dieser Feuerwehr ist derzeit nicht aktiv oder das Abo ist abgelaufen."));
        }

        SessionUser sessionUser = SessionUtil.from(user);
        session.setAttribute(SessionUtil.SESSION_USER_KEY, sessionUser);
        return ResponseEntity.ok(sessionUser);
    }


    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
        SessionUser sessionUser = SessionUtil.requireUser(session);
        if (request.currentPassword() == null || request.newPassword() == null || request.newPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Aktuelles und neues Passwort sind erforderlich."));
        }
        if (request.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("message", "Das neue Passwort muss mindestens 8 Zeichen lang sein."));
        }

        AppUser user = appUserRepository.findById(sessionUser.getUserId()).orElse(null);
        if (user == null || !passwordService.verifyPassword(request.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Aktuelles Passwort stimmt nicht."));
        }

        user.setPasswordHash(passwordService.hashPassword(request.newPassword()));
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Passwort wurde geändert."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(String username, String password) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}
