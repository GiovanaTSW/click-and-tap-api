package mx.edu.clickandtap.api.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.edu.clickandtap.api.domain.Role;
import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    private String phone;
    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean active;
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (role == null) role = Role.CUSTOMER;
    }
}