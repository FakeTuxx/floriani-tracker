package floriani_tracker.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "app_users",
        uniqueConstraints = @UniqueConstraint(columnNames = "username")
)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, length = 512)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.COLLECTOR;

    @ManyToOne(fetch = FetchType.EAGER)
    private FireDepartment fireDepartment;

    @Column(nullable = false)
    private boolean active = true;

    public AppUser() {
    }

    public AppUser(String username, String passwordHash, String displayName, UserRole role, FireDepartment fireDepartment) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.fireDepartment = fireDepartment;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public FireDepartment getFireDepartment() { return fireDepartment; }
    public boolean isActive() { return active; }

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setRole(UserRole role) { this.role = role; }
    public void setFireDepartment(FireDepartment fireDepartment) { this.fireDepartment = fireDepartment; }
    public void setActive(boolean active) { this.active = active; }
}
