package floriani_tracker.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "fire_departments")
public class FireDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Municipality municipality;

    @Column(nullable = false)
    private boolean active = true;

    private String defaultListName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TEST;

    private LocalDate subscriptionValidUntil;

    private String contactName;
    private String contactEmail;
    private String contactPhone;

    @Column(length = 1000)
    private String internalNote;

    public FireDepartment() {
    }

    public FireDepartment(String name, Municipality municipality, String defaultListName) {
        this.name = name;
        this.municipality = municipality;
        this.defaultListName = defaultListName;
        this.subscriptionStatus = SubscriptionStatus.TEST;
    }

    public boolean hasValidSubscription() {
        if (!active) return false;
        if (subscriptionStatus == SubscriptionStatus.BLOCKED || subscriptionStatus == SubscriptionStatus.EXPIRED) return false;
        if (subscriptionValidUntil == null) return true;
        return !subscriptionValidUntil.isBefore(LocalDate.now());
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Municipality getMunicipality() { return municipality; }
    public boolean isActive() { return active; }
    public String getDefaultListName() { return defaultListName; }
    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public LocalDate getSubscriptionValidUntil() { return subscriptionValidUntil; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getInternalNote() { return internalNote; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setMunicipality(Municipality municipality) { this.municipality = municipality; }
    public void setActive(boolean active) { this.active = active; }
    public void setDefaultListName(String defaultListName) { this.defaultListName = defaultListName; }
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public void setSubscriptionValidUntil(LocalDate subscriptionValidUntil) { this.subscriptionValidUntil = subscriptionValidUntil; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public void setInternalNote(String internalNote) { this.internalNote = internalNote; }
}
