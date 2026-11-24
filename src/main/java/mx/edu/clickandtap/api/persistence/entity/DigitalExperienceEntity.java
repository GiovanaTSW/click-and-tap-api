package mx.edu.clickandtap.api.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.edu.clickandtap.api.domain.ExperienceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "digital_experiences")
public class DigitalExperienceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "order_item_id")
    private Long orderItemId;
    @Enumerated(EnumType.STRING)
    private ExperienceType type;
    private String title;
    @Column(unique = true)
    private String slug;
    private String description;
    private boolean active;
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ExperienceLinkEntity> links = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}