package floriani_tracker.repository;

import floriani_tracker.model.FireDepartment;
import floriani_tracker.model.House;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HouseRepository extends JpaRepository<House, Long> {

    List<House> findByFireDepartmentAndListNameIgnoreCase(FireDepartment fireDepartment, String listName);

    List<House> findByFireDepartmentAndListNameIgnoreCaseAndDistrictIgnoreCase(FireDepartment fireDepartment, String listName, String district);

    Optional<House> findByFireDepartmentAndListNameIgnoreCaseAndAddressIgnoreCaseAndDistrictIgnoreCase(
            FireDepartment fireDepartment,
            String listName,
            String address,
            String district
    );

    void deleteByFireDepartmentAndListNameIgnoreCase(FireDepartment fireDepartment, String listName);

    @Query("select distinct h.listName from House h where h.fireDepartment = :fireDepartment and h.listName is not null order by h.listName")
    List<String> findDistinctListNamesByFireDepartment(@Param("fireDepartment") FireDepartment fireDepartment);

    List<House> findByFireDepartmentIsNull();

    // Alte Methoden bleiben absichtlich erhalten, damit bestehende H2-Daten sauber migriert werden können.
    List<House> findByListNameIgnoreCase(String listName);

    List<House> findByListNameIgnoreCaseAndDistrictIgnoreCase(String listName, String district);

    Optional<House> findByListNameIgnoreCaseAndAddressIgnoreCaseAndDistrictIgnoreCase(
            String listName,
            String address,
            String district
    );

    void deleteByListNameIgnoreCase(String listName);

    @Query("select distinct h.listName from House h where h.listName is not null order by h.listName")
    List<String> findDistinctListNames();
}
