package floriani_tracker.security;

import java.io.Serializable;

public class SessionUser implements Serializable {
    private Long userId;
    private String username;
    private String displayName;
    private String role;
    private Long fireDepartmentId;
    private String fireDepartmentName;
    private Long municipalityId;
    private String municipalityName;
    private String municipalityDistrict;
    private String municipalityState;
    private Double latitude;
    private Double longitude;
    private String subscriptionStatus;
    private String subscriptionValidUntil;
    private boolean superAdmin;

    public SessionUser() {
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public Long getFireDepartmentId() { return fireDepartmentId; }
    public String getFireDepartmentName() { return fireDepartmentName; }
    public Long getMunicipalityId() { return municipalityId; }
    public String getMunicipalityName() { return municipalityName; }
    public String getMunicipalityDistrict() { return municipalityDistrict; }
    public String getMunicipalityState() { return municipalityState; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public String getSubscriptionValidUntil() { return subscriptionValidUntil; }
    public boolean isSuperAdmin() { return superAdmin; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setRole(String role) { this.role = role; }
    public void setFireDepartmentId(Long fireDepartmentId) { this.fireDepartmentId = fireDepartmentId; }
    public void setFireDepartmentName(String fireDepartmentName) { this.fireDepartmentName = fireDepartmentName; }
    public void setMunicipalityId(Long municipalityId) { this.municipalityId = municipalityId; }
    public void setMunicipalityName(String municipalityName) { this.municipalityName = municipalityName; }
    public void setMunicipalityDistrict(String municipalityDistrict) { this.municipalityDistrict = municipalityDistrict; }
    public void setMunicipalityState(String municipalityState) { this.municipalityState = municipalityState; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public void setSubscriptionValidUntil(String subscriptionValidUntil) { this.subscriptionValidUntil = subscriptionValidUntil; }
    public void setSuperAdmin(boolean superAdmin) { this.superAdmin = superAdmin; }
}
