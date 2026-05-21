package floriani_tracker.repository;

import floriani_tracker.model.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MunicipalityRepository extends JpaRepository<Municipality, Long> {
    Optional<Municipality> findByGkz(String gkz);
    List<Municipality> findTop20ByStateIgnoreCaseAndNameContainingIgnoreCaseOrderByNameAsc(String state, String name);
    List<Municipality> findByStateIgnoreCaseOrderByNameAsc(String state);
}
