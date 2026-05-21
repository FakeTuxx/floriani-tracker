package floriani_tracker.controller;

import floriani_tracker.model.FireDepartment;
import floriani_tracker.model.House;
import floriani_tracker.model.HouseStatus;
import floriani_tracker.repository.FireDepartmentRepository;
import floriani_tracker.repository.HouseRepository;
import floriani_tracker.security.SessionUser;
import floriani_tracker.security.SessionUtil;
import floriani_tracker.security.NotAuthenticatedException;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/houses")
public class HouseController {

    private final HouseRepository houseRepository;
    private final FireDepartmentRepository fireDepartmentRepository;

    public HouseController(HouseRepository houseRepository, FireDepartmentRepository fireDepartmentRepository) {
        this.houseRepository = houseRepository;
        this.fireDepartmentRepository = fireDepartmentRepository;
    }

    @GetMapping
    public List<House> getHouses(
            @RequestParam(required = false) String listName,
            @RequestParam(required = false) String district,
            HttpSession session
    ) {
        FireDepartment fireDepartment = getCurrentFireDepartment(session);

        if (listName == null || listName.isBlank()) {
            return new ArrayList<>();
        }

        String safeListName = listName.trim();
        List<House> houses = getFilteredHouses(fireDepartment, safeListName, district);

        houses.sort(
                Comparator.comparing((House house) -> safeString(house.getStreet()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(HouseController::houseNumberSortKey, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(House::getAddress, String.CASE_INSENSITIVE_ORDER)
        );

        return houses;
    }

    @GetMapping("/lists")
    public List<String> getLists(HttpSession session) {
        FireDepartment fireDepartment = getCurrentFireDepartment(session);
        return houseRepository.findDistinctListNamesByFireDepartment(fireDepartment);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(
            @RequestParam(required = false) String listName,
            @RequestParam(required = false) String district,
            HttpSession session
    ) {
        FireDepartment fireDepartment = getCurrentFireDepartment(session);
        Map<String, Object> stats = new LinkedHashMap<>();

        if (listName == null || listName.isBlank()) {
            putEmptyStats(stats, "");
            return stats;
        }

        String safeListName = listName.trim();
        List<House> houses = getFilteredHouses(fireDepartment, safeListName, district);

        long total = houses.size();
        long open = houses.stream().filter(house -> house.getStatus() == HouseStatus.OFFEN).count();
        long later = houses.stream().filter(house -> house.getStatus() == HouseStatus.SPAETER_NOCHMAL).count();
        long done = houses.stream().filter(house -> house.getStatus() == HouseStatus.ERLEDIGT).count();
        long skipped = houses.stream().filter(house -> house.getStatus() == HouseStatus.UEBERSPRINGEN).count();
        long withMoney = houses.stream().filter(house -> house.getDonationAmount() != null && house.getDonationAmount() > 0).count();
        long withoutMoney = houses.stream().filter(house -> house.getDonationAmount() == null || house.getDonationAmount() <= 0).count();

        double donationSum = houses.stream()
                .map(House::getDonationAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        stats.put("listName", safeListName);
        stats.put("total", total);
        stats.put("open", open);
        stats.put("later", later);
        stats.put("done", done);
        stats.put("skipped", skipped);
        stats.put("withMoney", withMoney);
        stats.put("withoutMoney", withoutMoney);
        stats.put("donationSum", donationSum);
        stats.put("progressPercent", total == 0 ? 0 : Math.round((done * 100.0 / total) * 10.0) / 10.0);

        return stats;
    }

    @PutMapping("/{id}")
    public ResponseEntity<House> updateHouse(@PathVariable Long id, @RequestBody House updatedHouse, HttpSession session) {
        SessionUser user = SessionUtil.requireUser(session);
        if ("READ_ONLY".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        FireDepartment fireDepartment = getCurrentFireDepartment(session);

        return houseRepository.findById(id)
                .filter(house -> house.getFireDepartment() != null && Objects.equals(house.getFireDepartment().getId(), fireDepartment.getId()))
                .map(house -> {
                    house.setStatus(updatedHouse.getStatus() == null ? HouseStatus.OFFEN : updatedHouse.getStatus());
                    house.setDonationAmount(updatedHouse.getDonationAmount());
                    house.setResidentName(updatedHouse.getResidentName());
                    house.setNote(updatedHouse.getNote());
                    house.setUpdatedBy(user.getDisplayName());

                    if (updatedHouse.getDistrict() != null && !updatedHouse.getDistrict().isBlank()) {
                        house.setDistrict(updatedHouse.getDistrict());
                    }

                    if (updatedHouse.getStreet() != null && !updatedHouse.getStreet().isBlank()) {
                        house.setStreet(updatedHouse.getStreet());
                    }

                    if (updatedHouse.getHouseNumber() != null && !updatedHouse.getHouseNumber().isBlank()) {
                        house.setHouseNumber(updatedHouse.getHouseNumber());
                    }

                    if (updatedHouse.getLatitude() != 0 && updatedHouse.getLongitude() != 0) {
                        house.setLatitude(updatedHouse.getLatitude());
                        house.setLongitude(updatedHouse.getLongitude());
                    }

                    House savedHouse = houseRepository.save(house);
                    return ResponseEntity.ok(savedHouse);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public ResponseEntity<List<House>> importHouses(
            @RequestParam String listName,
            @RequestBody List<House> importedHouses,
            HttpSession session
    ) {
        SessionUser user = SessionUtil.requireUser(session);
        if ("READ_ONLY".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        FireDepartment fireDepartment = getCurrentFireDepartment(session);

        if (listName == null || listName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String safeListName = listName.trim();
        List<House> savedHouses = new ArrayList<>();

        for (House importedHouse : importedHouses) {
            if (importedHouse.getAddress() == null || importedHouse.getAddress().isBlank()) {
                continue;
            }

            if (importedHouse.getDistrict() == null || importedHouse.getDistrict().isBlank()) {
                importedHouse.setDistrict(fireDepartment.getMunicipality().getName());
            }

            if (importedHouse.getLatitude() == 0 || importedHouse.getLongitude() == 0) {
                continue;
            }

            House house = houseRepository
                    .findByFireDepartmentAndListNameIgnoreCaseAndAddressIgnoreCaseAndDistrictIgnoreCase(
                            fireDepartment,
                            safeListName,
                            importedHouse.getAddress(),
                            importedHouse.getDistrict()
                    )
                    .orElseGet(House::new);

            if (house.getId() == null) {
                house.setStatus(HouseStatus.OFFEN);
                house.setDonationAmount(null);
                house.setNote(null);
            }

            house.setFireDepartment(fireDepartment);
            house.setListName(safeListName);
            house.setAddress(importedHouse.getAddress());
            house.setStreet(importedHouse.getStreet());
            house.setHouseNumber(importedHouse.getHouseNumber());
            house.setResidentName(importedHouse.getResidentName());
            house.setDistrict(importedHouse.getDistrict());
            house.setLatitude(importedHouse.getLatitude());
            house.setLongitude(importedHouse.getLongitude());

            savedHouses.add(houseRepository.save(house));
        }

        savedHouses.sort(
                Comparator.comparing((House house) -> safeString(house.getStreet()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(HouseController::houseNumberSortKey, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(House::getAddress, String.CASE_INSENSITIVE_ORDER)
        );

        return ResponseEntity.ok(savedHouses);
    }

    @Transactional
    @DeleteMapping
    public ResponseEntity<Void> deleteHousesByList(@RequestParam String listName, HttpSession session) {
        SessionUser user = SessionUtil.requireUser(session);
        if ("READ_ONLY".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        FireDepartment fireDepartment = getCurrentFireDepartment(session);

        if (listName == null || listName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        houseRepository.deleteByFireDepartmentAndListNameIgnoreCase(fireDepartment, listName.trim());
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/lists/duplicate")
    @Transactional
    public ResponseEntity<?> duplicateList(@RequestBody DuplicateListRequest request, HttpSession session) {
        SessionUser user = SessionUtil.requireUser(session);
        if ("READ_ONLY".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        FireDepartment fireDepartment = getCurrentFireDepartment(session);

        if (request.sourceListName() == null || request.sourceListName().isBlank() ||
                request.targetListName() == null || request.targetListName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Quellliste und neue Liste sind erforderlich."));
        }

        String source = request.sourceListName().trim();
        String target = request.targetListName().trim();
        if (source.equalsIgnoreCase(target)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Die neue Liste muss anders heißen als die Quellliste."));
        }

        List<House> existingTarget = houseRepository.findByFireDepartmentAndListNameIgnoreCase(fireDepartment, target);
        if (!existingTarget.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Es gibt bereits eine Liste mit diesem Namen."));
        }

        List<House> sourceHouses = houseRepository.findByFireDepartmentAndListNameIgnoreCase(fireDepartment, source);
        if (sourceHouses.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Die Quellliste enthält keine Häuser."));
        }

        boolean resetValues = request.resetValues() == null || request.resetValues();
        List<House> copied = new ArrayList<>();
        for (House original : sourceHouses) {
            House copy = new House();
            copy.setFireDepartment(fireDepartment);
            copy.setListName(target);
            copy.setAddress(original.getAddress());
            copy.setStreet(original.getStreet());
            copy.setHouseNumber(original.getHouseNumber());
            copy.setResidentName(original.getResidentName());
            copy.setDistrict(original.getDistrict());
            copy.setLatitude(original.getLatitude());
            copy.setLongitude(original.getLongitude());
            copy.setUpdatedBy(user.getDisplayName());
            if (resetValues) {
                copy.setStatus(HouseStatus.OFFEN);
                copy.setDonationAmount(null);
                copy.setNote(null);
            } else {
                copy.setStatus(original.getStatus());
                copy.setDonationAmount(original.getDonationAmount());
                copy.setNote(original.getNote());
            }
            copied.add(houseRepository.save(copy));
        }

        return ResponseEntity.ok(Map.of(
                "sourceListName", source,
                "targetListName", target,
                "copied", copied.size(),
                "resetValues", resetValues
        ));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam String listName,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "ALL") String statusFilter,
            @RequestParam(required = false, defaultValue = "ALL") String moneyFilter,
            @RequestParam(required = false) Double moneyValue,
            @RequestParam(required = false, defaultValue = "ADDRESS_ASC") String sort,
            HttpSession session
    ) {
        FireDepartment fireDepartment = getCurrentFireDepartment(session);
        if (listName == null || listName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<House> houses = houseRepository.findByFireDepartmentAndListNameIgnoreCase(fireDepartment, listName.trim());
        houses = applyExportFilters(houses, search, statusFilter, moneyFilter, moneyValue, sort);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sammelliste");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIndex = 0;
            Row title = sheet.createRow(rowIndex++);
            title.createCell(0).setCellValue(listName.trim());
            Row info = sheet.createRow(rowIndex++);
            info.createCell(0).setCellValue(fireDepartment.getName() + " · " + fireDepartment.getMunicipality().getName());
            Row filter = sheet.createRow(rowIndex++);
            filter.createCell(0).setCellValue("Filter: " + describeFilters(search, statusFilter, moneyFilter, moneyValue));
            rowIndex++;

            Row header = sheet.createRow(rowIndex++);
            String[] columns = {"Adresse", "Name/Familie", "Status", "Spende EUR", "Notiz", "Ortsteil/Gemeinde", "Zuletzt bearbeitet", "Bearbeitet von"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            for (House house : houses) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(safeString(house.getAddress()));
                row.createCell(1).setCellValue(safeString(house.getResidentName()));
                row.createCell(2).setCellValue(house.getStatus() == null ? "Offen" : house.getStatus().name());
                if (house.getDonationAmount() != null) row.createCell(3).setCellValue(house.getDonationAmount());
                else row.createCell(3).setCellValue("");
                row.createCell(4).setCellValue(safeString(house.getNote()));
                row.createCell(5).setCellValue(safeString(house.getDistrict()));
                row.createCell(6).setCellValue(house.getUpdatedAt() == null ? "" : house.getUpdatedAt().format(formatter));
                row.createCell(7).setCellValue(safeString(house.getUpdatedBy()));
            }

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);

            String filename = sanitizeFilename(listName.trim()) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private FireDepartment getCurrentFireDepartment(HttpSession session) {
        SessionUser user = SessionUtil.requireUser(session);
        FireDepartment department = fireDepartmentRepository.findById(user.getFireDepartmentId()).orElseThrow();
        if (!department.hasValidSubscription()) {
            throw new NotAuthenticatedException();
        }
        return department;
    }

    private List<House> getFilteredHouses(FireDepartment fireDepartment, String listName, String district) {
        if (district == null || district.isBlank() || district.equalsIgnoreCase("ALLE")) {
            return houseRepository.findByFireDepartmentAndListNameIgnoreCase(fireDepartment, listName);
        }

        return houseRepository.findByFireDepartmentAndListNameIgnoreCaseAndDistrictIgnoreCase(fireDepartment, listName, district);
    }

    private void putEmptyStats(Map<String, Object> stats, String listName) {
        stats.put("listName", listName);
        stats.put("total", 0);
        stats.put("open", 0);
        stats.put("later", 0);
        stats.put("done", 0);
        stats.put("skipped", 0);
        stats.put("withMoney", 0);
        stats.put("withoutMoney", 0);
        stats.put("donationSum", 0.0);
        stats.put("progressPercent", 0);
    }


    private List<House> applyExportFilters(List<House> houses, String search, String statusFilter, String moneyFilter, Double moneyValue, String sort) {
        String normalizedSearch = normalizeText(search);
        List<House> result = houses.stream().filter(house -> {
            if (normalizedSearch != null && !normalizedSearch.isBlank()) {
                String haystack = normalizeText(
                        safeString(house.getAddress()) + " " + safeString(house.getStreet()) + " " +
                                safeString(house.getHouseNumber()) + " " + safeString(house.getResidentName()) + " " +
                                safeString(house.getNote()) + " " + safeString(house.getDistrict())
                );
                if (!haystack.contains(normalizedSearch)) return false;
            }

            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
                if (house.getStatus() == null || !house.getStatus().name().equalsIgnoreCase(statusFilter)) return false;
            }

            double amount = house.getDonationAmount() == null ? 0.0 : house.getDonationAmount();
            String mf = moneyFilter == null ? "ALL" : moneyFilter;
            if ("WITH_MONEY".equalsIgnoreCase(mf) && amount <= 0) return false;
            if ("WITHOUT_MONEY".equalsIgnoreCase(mf) && amount > 0) return false;
            if ("MIN".equalsIgnoreCase(mf) && moneyValue != null && amount < moneyValue) return false;
            if ("MAX".equalsIgnoreCase(mf) && moneyValue != null && amount > moneyValue) return false;
            return true;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Comparator<House> comparator = Comparator.comparing((House house) -> safeString(house.getStreet()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HouseController::houseNumberSortKey, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(House::getAddress, String.CASE_INSENSITIVE_ORDER);
        if ("STATUS_ASC".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((House house) -> house.getStatus() == null ? 0 : house.getStatus().ordinal())
                    .thenComparing(comparator);
        } else if ("MONEY_DESC".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((House house) -> house.getDonationAmount() == null ? 0.0 : house.getDonationAmount()).reversed()
                    .thenComparing(comparator);
        } else if ("MONEY_ASC".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((House house) -> house.getDonationAmount() == null ? 0.0 : house.getDonationAmount())
                    .thenComparing(comparator);
        } else if ("UPDATED_DESC".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((House house) -> house.getUpdatedAt() == null ? java.time.LocalDateTime.MIN : house.getUpdatedAt()).reversed()
                    .thenComparing(comparator);
        }
        result.sort(comparator);
        return result;
    }

    private String describeFilters(String search, String statusFilter, String moneyFilter, Double moneyValue) {
        List<String> parts = new ArrayList<>();
        if (search != null && !search.isBlank()) parts.add("Suche: " + search.trim());
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) parts.add("Status: " + statusFilter);
        if (moneyFilter != null && !"ALL".equalsIgnoreCase(moneyFilter)) {
            String label = switch (moneyFilter.toUpperCase(Locale.ROOT)) {
                case "WITH_MONEY" -> "mit Betrag";
                case "WITHOUT_MONEY" -> "ohne Betrag";
                case "MIN" -> "mindestens " + (moneyValue == null ? "?" : moneyValue) + " EUR";
                case "MAX" -> "höchstens " + (moneyValue == null ? "?" : moneyValue) + " EUR";
                default -> moneyFilter;
            };
            parts.add("Geld: " + label);
        }
        return parts.isEmpty() ? "keine" : String.join(", ", parts);
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace("straße", "strasse")
                .replace("str.", "strasse");
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String sanitizeFilename(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    public record DuplicateListRequest(String sourceListName, String targetListName, Boolean resetValues) {}

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static String houseNumberSortKey(House house) {
        String number = safeString(house.getHouseNumber());
        return number.isBlank() ? safeString(house.getAddress()) : number;
    }
}
