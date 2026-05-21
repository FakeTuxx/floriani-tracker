package floriani_tracker.controller;

import floriani_tracker.model.*;
import floriani_tracker.repository.AppUserRepository;
import floriani_tracker.repository.FireDepartmentRepository;
import floriani_tracker.repository.HouseRepository;
import floriani_tracker.repository.MunicipalityRepository;
import floriani_tracker.security.PasswordService;
import floriani_tracker.security.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final FireDepartmentRepository fireDepartmentRepository;
    private final MunicipalityRepository municipalityRepository;
    private final AppUserRepository appUserRepository;
    private final HouseRepository houseRepository;
    private final PasswordService passwordService = new PasswordService();

    public AdminController(FireDepartmentRepository fireDepartmentRepository,
                           MunicipalityRepository municipalityRepository,
                           AppUserRepository appUserRepository,
                           HouseRepository houseRepository) {
        this.fireDepartmentRepository = fireDepartmentRepository;
        this.municipalityRepository = municipalityRepository;
        this.appUserRepository = appUserRepository;
        this.houseRepository = houseRepository;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        List<FireDepartment> departments = fireDepartmentRepository.findAll().stream()
                .filter(department -> !"System Administration".equalsIgnoreCase(department.getName()))
                .toList();
        long active = departments.stream().filter(FireDepartment::hasValidSubscription).count();
        long blocked = departments.stream().filter(d -> !d.hasValidSubscription()).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fireDepartments", departments.size());
        result.put("active", active);
        result.put("blockedOrExpired", blocked);
        result.put("users", appUserRepository.count());
        result.put("houses", houseRepository.count());
        return result;
    }

    @GetMapping("/fire-departments")
    public List<Map<String, Object>> fireDepartments(@RequestParam(required = false) String search, HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        List<FireDepartment> departments = (search == null || search.isBlank())
                ? fireDepartmentRepository.findAll()
                : fireDepartmentRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
        departments = departments.stream()
                .filter(department -> !"System Administration".equalsIgnoreCase(department.getName()))
                .sorted(Comparator.comparing(FireDepartment::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return departments.stream().map(this::toDepartmentDto).toList();
    }

    @PostMapping("/fire-departments")
    @Transactional
    public ResponseEntity<?> createFireDepartment(@RequestBody CreateFireDepartmentRequest request, HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        if (isBlank(request.fireDepartmentName()) || isBlank(request.username()) || isBlank(request.password()) || isBlank(request.municipalityName())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Feuerwehrname, Gemeinde, Benutzername und Passwort sind erforderlich."));
        }
        if (appUserRepository.existsByUsernameIgnoreCase(request.username().trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dieser Benutzername ist bereits vergeben."));
        }

        Municipality municipality = findOrCreateMunicipality(request);
        FireDepartment department = new FireDepartment(request.fireDepartmentName().trim(), municipality,
                defaultIfBlank(request.defaultListName(), "Florianisammlung 2026"));
        department.setActive(true);
        department.setSubscriptionStatus(parseStatus(request.subscriptionStatus(), SubscriptionStatus.TEST));
        department.setSubscriptionValidUntil(parseDate(request.subscriptionValidUntil()));
        department.setContactName(emptyToNull(request.contactName()));
        department.setContactEmail(emptyToNull(request.contactEmail()));
        department.setContactPhone(emptyToNull(request.contactPhone()));
        department.setInternalNote(emptyToNull(request.internalNote()));
        department = fireDepartmentRepository.save(department);

        AppUser adminUser = new AppUser(
                request.username().trim(),
                passwordService.hashPassword(request.password()),
                defaultIfBlank(request.displayName(), request.fireDepartmentName().trim()),
                UserRole.ADMIN,
                department
        );
        appUserRepository.save(adminUser);
        return ResponseEntity.ok(toDepartmentDto(department));
    }

    @PutMapping("/fire-departments/{id}")
    @Transactional
    public ResponseEntity<?> updateFireDepartment(@PathVariable Long id, @RequestBody UpdateFireDepartmentRequest request, HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        FireDepartment department = fireDepartmentRepository.findById(id).orElse(null);
        if (department == null) return ResponseEntity.notFound().build();

        if (!isBlank(request.name())) department.setName(request.name().trim());
        if (!isBlank(request.defaultListName())) department.setDefaultListName(request.defaultListName().trim());
        if (request.active() != null) department.setActive(request.active());
        if (!isBlank(request.subscriptionStatus())) department.setSubscriptionStatus(parseStatus(request.subscriptionStatus(), department.getSubscriptionStatus()));
        if (request.subscriptionValidUntil() != null) department.setSubscriptionValidUntil(parseDate(request.subscriptionValidUntil()));
        department.setContactName(emptyToNull(request.contactName()));
        department.setContactEmail(emptyToNull(request.contactEmail()));
        department.setContactPhone(emptyToNull(request.contactPhone()));
        department.setInternalNote(emptyToNull(request.internalNote()));
        return ResponseEntity.ok(toDepartmentDto(fireDepartmentRepository.save(department)));
    }

    @GetMapping("/fire-departments/{id}/users")
    public ResponseEntity<?> users(@PathVariable Long id, HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        FireDepartment department = fireDepartmentRepository.findById(id).orElse(null);
        if (department == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(appUserRepository.findByFireDepartmentOrderByUsernameAsc(department).stream().map(this::toUserDto).toList());
    }

    @PostMapping("/fire-departments/{id}/users")
    @Transactional
    public ResponseEntity<?> createUser(@PathVariable Long id, @RequestBody CreateUserRequest request, HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        FireDepartment department = fireDepartmentRepository.findById(id).orElse(null);
        if (department == null) return ResponseEntity.notFound().build();
        if (isBlank(request.username()) || isBlank(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Benutzername und Passwort sind erforderlich."));
        }
        if (appUserRepository.existsByUsernameIgnoreCase(request.username().trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dieser Benutzername ist bereits vergeben."));
        }
        UserRole role = parseRole(request.role(), UserRole.COLLECTOR);
        if (role == UserRole.SUPER_ADMIN) role = UserRole.ADMIN;
        AppUser user = new AppUser(
                request.username().trim(),
                passwordService.hashPassword(request.password()),
                defaultIfBlank(request.displayName(), request.username().trim()),
                role,
                department
        );
        user.setActive(request.active() == null || request.active());
        return ResponseEntity.ok(toUserDto(appUserRepository.save(user)));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request, HttpSession session) {
        SessionUtil.requireSuperAdmin(session);
        AppUser user = appUserRepository.findById(id).orElse(null);
        if (user == null || user.getRole() == UserRole.SUPER_ADMIN) return ResponseEntity.notFound().build();
        if (!isBlank(request.displayName())) user.setDisplayName(request.displayName().trim());
        if (!isBlank(request.role())) user.setRole(parseRole(request.role(), user.getRole()));
        if (request.active() != null) user.setActive(request.active());
        if (!isBlank(request.password())) user.setPasswordHash(passwordService.hashPassword(request.password()));
        return ResponseEntity.ok(toUserDto(appUserRepository.save(user)));
    }

    private Municipality findOrCreateMunicipality(CreateFireDepartmentRequest request) {
        if (!isBlank(request.gkz())) {
            Optional<Municipality> existing = municipalityRepository.findByGkz(request.gkz().trim());
            if (existing.isPresent()) return existing.get();
        }
        String generatedGkz = !isBlank(request.gkz()) ? request.gkz().trim() : "CUSTOM-" + UUID.randomUUID();
        Municipality municipality = new Municipality(
                generatedGkz,
                request.municipalityName().trim(),
                defaultIfBlank(request.district(), request.municipalityName().trim()),
                defaultIfBlank(request.state(), "Steiermark"),
                request.latitude(),
                request.longitude()
        );
        return municipalityRepository.save(municipality);
    }

    private Map<String, Object> toDepartmentDto(FireDepartment department) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", department.getId());
        dto.put("name", department.getName());
        dto.put("active", department.isActive());
        dto.put("canUse", department.hasValidSubscription());
        dto.put("defaultListName", department.getDefaultListName());
        dto.put("subscriptionStatus", department.getSubscriptionStatus());
        dto.put("subscriptionValidUntil", department.getSubscriptionValidUntil());
        dto.put("contactName", department.getContactName());
        dto.put("contactEmail", department.getContactEmail());
        dto.put("contactPhone", department.getContactPhone());
        dto.put("internalNote", department.getInternalNote());
        dto.put("municipality", department.getMunicipality());
        dto.put("userCount", appUserRepository.findByFireDepartmentOrderByUsernameAsc(department).size());
        dto.put("listCount", houseRepository.findDistinctListNamesByFireDepartment(department).size());
        return dto;
    }

    private Map<String, Object> toUserDto(AppUser user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("displayName", user.getDisplayName());
        dto.put("role", user.getRole());
        dto.put("active", user.isActive());
        dto.put("fireDepartmentId", user.getFireDepartment() == null ? null : user.getFireDepartment().getId());
        return dto;
    }

    private SubscriptionStatus parseStatus(String value, SubscriptionStatus fallback) {
        if (isBlank(value)) return fallback;
        try { return SubscriptionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return fallback; }
    }

    private UserRole parseRole(String value, UserRole fallback) {
        if (isBlank(value)) return fallback;
        try { return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return fallback; }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value.trim());
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private String defaultIfBlank(String value, String fallback) { return isBlank(value) ? fallback : value.trim(); }
    private String emptyToNull(String value) { return isBlank(value) ? null : value.trim(); }

    public record CreateFireDepartmentRequest(
            String fireDepartmentName,
            String municipalityName,
            String gkz,
            String district,
            String state,
            Double latitude,
            Double longitude,
            String defaultListName,
            String username,
            String password,
            String displayName,
            String subscriptionStatus,
            String subscriptionValidUntil,
            String contactName,
            String contactEmail,
            String contactPhone,
            String internalNote
    ) {}

    public record UpdateFireDepartmentRequest(
            String name,
            Boolean active,
            String defaultListName,
            String subscriptionStatus,
            String subscriptionValidUntil,
            String contactName,
            String contactEmail,
            String contactPhone,
            String internalNote
    ) {}

    public record CreateUserRequest(String username, String password, String displayName, String role, Boolean active) {}
    public record UpdateUserRequest(String password, String displayName, String role, Boolean active) {}
}
