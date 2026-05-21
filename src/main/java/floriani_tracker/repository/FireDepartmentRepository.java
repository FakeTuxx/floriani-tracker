package floriani_tracker.repository;

import floriani_tracker.model.FireDepartment;
import floriani_tracker.model.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FireDepartmentRepository extends JpaRepository<FireDepartment, Long> {
    Optional<FireDepartment> findByNameIgnoreCase(String name);
    Optional<FireDepartment> findFirstByMunicipality(Municipality municipality);
    List<FireDepartment> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
