package floriani_tracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class House {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private FireDepartment fireDepartment;

    private String listName;

    private String address;

    private String street;

    private String houseNumber;

    private String residentName;

    private String district;

    private double latitude;

    private double longitude;

    @Enumerated(EnumType.STRING)
    private HouseStatus status = HouseStatus.OFFEN;

    private Double donationAmount;

    @Column(length = 1000)
    private String note;

    private String updatedBy;

    private LocalDateTime updatedAt;

    public House() {
    }

    public House(String listName, String address, String district, double latitude, double longitude,
                 HouseStatus status, Double donationAmount, String note) {
        this.listName = listName;
        this.address = address;
        this.district = district;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.donationAmount = donationAmount;
        this.note = note;
    }

    @PrePersist
    @PreUpdate
    public void beforeSave() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public FireDepartment getFireDepartment() { return fireDepartment; }
    public String getListName() { return listName; }
    public String getAddress() { return address; }
    public String getStreet() { return street; }
    public String getHouseNumber() { return houseNumber; }
    public String getResidentName() { return residentName; }
    public String getDistrict() { return district; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public HouseStatus getStatus() { return status; }
    public Double getDonationAmount() { return donationAmount; }
    public String getNote() { return note; }
    public String getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setFireDepartment(FireDepartment fireDepartment) { this.fireDepartment = fireDepartment; }
    public void setListName(String listName) { this.listName = listName; }
    public void setAddress(String address) { this.address = address; }
    public void setStreet(String street) { this.street = street; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }
    public void setResidentName(String residentName) { this.residentName = residentName; }
    public void setDistrict(String district) { this.district = district; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setStatus(HouseStatus status) { this.status = status; }
    public void setDonationAmount(Double donationAmount) { this.donationAmount = donationAmount; }
    public void setNote(String note) { this.note = note; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
