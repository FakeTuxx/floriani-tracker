package floriani_tracker.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "municipalities",
        uniqueConstraints = @UniqueConstraint(columnNames = "gkz")
)
public class Municipality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String gkz;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String state;

    private Double latitude;

    private Double longitude;

    public Municipality() {
    }

    public Municipality(String gkz, String name, String district, String state, Double latitude, Double longitude) {
        this.gkz = gkz;
        this.name = name;
        this.district = district;
        this.state = state;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public String getGkz() { return gkz; }
    public String getName() { return name; }
    public String getDistrict() { return district; }
    public String getState() { return state; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    public void setId(Long id) { this.id = id; }
    public void setGkz(String gkz) { this.gkz = gkz; }
    public void setName(String name) { this.name = name; }
    public void setDistrict(String district) { this.district = district; }
    public void setState(String state) { this.state = state; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
