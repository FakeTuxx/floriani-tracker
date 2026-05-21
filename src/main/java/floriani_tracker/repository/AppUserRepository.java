package floriani_tracker.repository;

import floriani_tracker.model.AppUser;
import floriani_tracker.model.FireDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<AppUser> findByFireDepartmentOrderByUsernameAsc(FireDepartment fireDepartment);
}
