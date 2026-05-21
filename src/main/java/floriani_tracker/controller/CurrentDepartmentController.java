package floriani_tracker.controller;

import floriani_tracker.model.FireDepartment;
import floriani_tracker.repository.FireDepartmentRepository;
import floriani_tracker.security.SessionUser;
import floriani_tracker.security.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/department")
public class CurrentDepartmentController {

    private final FireDepartmentRepository fireDepartmentRepository;

    public CurrentDepartmentController(FireDepartmentRepository fireDepartmentRepository) {
        this.fireDepartmentRepository = fireDepartmentRepository;
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> currentDepartment(HttpSession session) {
        SessionUser user = SessionUtil.requireUser(session);

        Map<String, Object> body = new LinkedHashMap<>();
        if (user.isSuperAdmin()) {
            body.put("superAdmin", true);
            body.put("name", "Floriani Tracker Verwaltung");
            body.put("defaultListName", "");
            body.put("municipality", null);
            return ResponseEntity.ok(body);
        }

        FireDepartment department = fireDepartmentRepository.findById(user.getFireDepartmentId()).orElseThrow();
        if (!department.hasValidSubscription()) {
            return ResponseEntity.status(403).body(Map.of("message", "Der Zugang dieser Feuerwehr ist nicht aktiv oder abgelaufen."));
        }

        body.put("superAdmin", false);
        body.put("id", department.getId());
        body.put("name", department.getName());
        body.put("defaultListName", department.getDefaultListName());
        body.put("subscriptionStatus", department.getSubscriptionStatus());
        body.put("subscriptionValidUntil", department.getSubscriptionValidUntil());
        body.put("municipality", department.getMunicipality());
        return ResponseEntity.ok(body);
    }
}
