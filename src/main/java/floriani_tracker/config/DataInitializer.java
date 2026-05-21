package floriani_tracker.config;

import floriani_tracker.model.*;
import floriani_tracker.repository.AppUserRepository;
import floriani_tracker.repository.FireDepartmentRepository;
import floriani_tracker.repository.HouseRepository;
import floriani_tracker.repository.MunicipalityRepository;
import floriani_tracker.security.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "floriani2026";
    private static final String DEFAULT_LIST_NAME = "Florianisammlung 2026";

    private final MunicipalityRepository municipalityRepository;
    private final FireDepartmentRepository fireDepartmentRepository;
    private final AppUserRepository appUserRepository;
    private final HouseRepository houseRepository;
    private final PasswordService passwordService = new PasswordService();

    @Value("${app.demo-data.enabled:true}")
    private boolean demoDataEnabled;

    @Value("${app.super-admin.username:admin}")
    private String superAdminUsername;

    @Value("${app.super-admin.password:admin2026}")
    private String superAdminPassword;

    @Value("${app.super-admin.display-name:System Admin}")
    private String superAdminDisplayName;

    public DataInitializer(MunicipalityRepository municipalityRepository,
                           FireDepartmentRepository fireDepartmentRepository,
                           AppUserRepository appUserRepository,
                           HouseRepository houseRepository) {
        this.municipalityRepository = municipalityRepository;
        this.fireDepartmentRepository = fireDepartmentRepository;
        this.appUserRepository = appUserRepository;
        this.houseRepository = houseRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedStarterMunicipalities();
        seedSuperAdmin();
        if (demoDataEnabled) {
            seedDemoFireDepartmentsAndUsers();
            attachOldHousesToWundschuh();
        }
    }

    private void seedSuperAdmin() {
        Municipality systemMunicipality = municipalityRepository.findByGkz("SYS")
                .orElseGet(() -> municipalityRepository.save(
                        new Municipality("SYS", "System", "System", "Österreich", 47.5, 14.5)
                ));

        FireDepartment systemDepartment = fireDepartmentRepository.findByNameIgnoreCase("System Administration")
                .orElseGet(() -> fireDepartmentRepository.save(
                        new FireDepartment("System Administration", systemMunicipality, "")
                ));
        systemDepartment.setActive(true);
        systemDepartment.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        fireDepartmentRepository.save(systemDepartment);

        String username = (superAdminUsername == null || superAdminUsername.isBlank()) ? "admin" : superAdminUsername.trim();
        String password = (superAdminPassword == null || superAdminPassword.isBlank()) ? "admin2026" : superAdminPassword;
        String displayName = (superAdminDisplayName == null || superAdminDisplayName.isBlank()) ? "System Admin" : superAdminDisplayName.trim();

        if (!appUserRepository.existsByUsernameIgnoreCase(username)) {
            AppUser admin = new AppUser(
                    username,
                    passwordService.hashPassword(password),
                    displayName,
                    UserRole.SUPER_ADMIN,
                    systemDepartment
            );
            appUserRepository.save(admin);
        }
    }

    /**
     * Erstellt für jede vorbereitete Gemeinde eine eigene Feuerwehr mit eigenem Login.
     * Damit kann man sofort testen, ob die App pro Feuerwehr/Gemeinde sauber getrennt arbeitet.
     */
    private void seedDemoFireDepartmentsAndUsers() {
        List<Municipality> municipalities = municipalityRepository.findAll();

        for (Municipality municipality : municipalities) {
            if ("SYS".equalsIgnoreCase(municipality.getGkz())) {
                continue;
            }
            String fireDepartmentName = "FF " + municipality.getName();
            FireDepartment department = fireDepartmentRepository.findByNameIgnoreCase(fireDepartmentName)
                    .orElseGet(() -> fireDepartmentRepository.save(
                            new FireDepartment(fireDepartmentName, municipality, DEFAULT_LIST_NAME)
                    ));

            if (department.getDefaultListName() == null || department.getDefaultListName().isBlank()) {
                department.setDefaultListName(DEFAULT_LIST_NAME);
            }
            if (department.getSubscriptionStatus() == null) {
                department.setSubscriptionStatus(SubscriptionStatus.TEST);
            }
            fireDepartmentRepository.save(department);

            String username = usernameForMunicipality(municipality.getName());
            if (!appUserRepository.existsByUsernameIgnoreCase(username)) {
                AppUser user = new AppUser(
                        username,
                        passwordService.hashPassword(DEMO_PASSWORD),
                        fireDepartmentName,
                        UserRole.ADMIN,
                        department
                );
                appUserRepository.save(user);
            }
        }
    }

    private void attachOldHousesToWundschuh() {
        Municipality wundschuh = municipalityRepository.findByGkz("60653")
                .orElseGet(() -> municipalityRepository.save(
                        new Municipality("60653", "Wundschuh", "Graz-Umgebung", "Steiermark", 46.9278, 15.4534)
                ));

        FireDepartment department = fireDepartmentRepository.findByNameIgnoreCase("FF Wundschuh")
                .orElseGet(() -> fireDepartmentRepository.save(new FireDepartment("FF Wundschuh", wundschuh, DEFAULT_LIST_NAME)));

        List<House> orphanHouses = houseRepository.findByFireDepartmentIsNull();
        for (House house : orphanHouses) {
            house.setFireDepartment(department);
            if (house.getStreet() == null || house.getStreet().isBlank() || house.getHouseNumber() == null || house.getHouseNumber().isBlank()) {
                fillStreetAndHouseNumber(house);
            }
            houseRepository.save(house);
        }
    }

    private void seedStarterMunicipalities() {
        saveMunicipality("60653", "Wundschuh", "Graz-Umgebung", 46.9278, 15.4534);
        saveMunicipality("60101", "Graz", "Graz", 47.0707, 15.4395);
        saveMunicipality("60617", "Feldkirchen bei Graz", "Graz-Umgebung", 47.0167, 15.45);
        saveMunicipality("60641", "Seiersberg-Pirka", "Graz-Umgebung", 47.0005, 15.3977);
        saveMunicipality("60645", "Premstätten", "Graz-Umgebung", 46.9645, 15.4049);
        saveMunicipality("60629", "Kalsdorf bei Graz", "Graz-Umgebung", 46.9653, 15.4805);
        saveMunicipality("61027", "Leibnitz", "Leibnitz", 46.7816, 15.5382);
        saveMunicipality("61730", "Gleisdorf", "Weiz", 47.1056, 15.7101);
        saveMunicipality("61760", "Weiz", "Weiz", 47.2172, 15.6256);
        saveMunicipality("60201", "Bruck an der Mur", "Bruck-Mürzzuschlag", 47.4098, 15.2708);
        saveMunicipality("62141", "Mürzzuschlag", "Bruck-Mürzzuschlag", 47.6066, 15.6722);
        saveMunicipality("61012", "Gralla", "Leibnitz", 46.8144, 15.5559);
        saveMunicipality("61013", "Heiligenkreuz am Waasen", "Leibnitz", 46.9556, 15.5886);
        saveMunicipality("60305", "Deutschlandsberg", "Deutschlandsberg", 46.8153, 15.2227);
        saveMunicipality("60329", "Stainz", "Deutschlandsberg", 46.8947, 15.2677);
        saveMunicipality("60710", "Hartberg", "Hartberg-Fürstenfeld", 47.2792, 15.9692);
        saveMunicipality("62220", "Fürstenfeld", "Hartberg-Fürstenfeld", 47.05, 16.0833);
        saveMunicipality("62036", "Judenburg", "Murtal", 47.1667, 14.6667);
        saveMunicipality("62032", "Knittelfeld", "Murtal", 47.2167, 14.8167);
        saveMunicipality("61108", "Leoben", "Leoben", 47.3817, 15.0972);
        saveMunicipality("61213", "Liezen", "Liezen", 47.5667, 14.25);
    }

    private void saveMunicipality(String gkz, String name, String district, Double latitude, Double longitude) {
        municipalityRepository.findByGkz(gkz).orElseGet(() ->
                municipalityRepository.save(new Municipality(gkz, name, district, "Steiermark", latitude, longitude))
        );
    }

    private String usernameForMunicipality(String name) {
        String prepared = name.toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue");

        String normalized = Normalizer.normalize(prepared, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "")
                .trim();

        if (normalized.isBlank()) {
            return "feuerwehr";
        }
        return normalized;
    }

    private void fillStreetAndHouseNumber(House house) {
        String address = house.getAddress();
        if (address == null || address.isBlank()) {
            return;
        }

        String trimmed = address.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace < trimmed.length() - 1) {
            house.setStreet(trimmed.substring(0, lastSpace));
            house.setHouseNumber(trimmed.substring(lastSpace + 1));
        } else {
            house.setStreet(trimmed);
            house.setHouseNumber("");
        }
    }
}
