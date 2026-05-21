package floriani_tracker.controller;

import floriani_tracker.model.Municipality;
import floriani_tracker.repository.MunicipalityRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/municipalities")
public class MunicipalityController {

    private final MunicipalityRepository municipalityRepository;

    public MunicipalityController(MunicipalityRepository municipalityRepository) {
        this.municipalityRepository = municipalityRepository;
    }

    @GetMapping
    public List<Municipality> search(
            @RequestParam(defaultValue = "Steiermark") String state,
            @RequestParam(required = false) String search
    ) {
        if (search == null || search.isBlank()) {
            return municipalityRepository.findByStateIgnoreCaseOrderByNameAsc(state).stream().limit(50).toList();
        }
        return municipalityRepository.findTop20ByStateIgnoreCaseAndNameContainingIgnoreCaseOrderByNameAsc(state, search.trim());
    }
}
