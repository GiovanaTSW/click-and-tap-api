package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.List;

@Getter @Setter
public class DigitalExperience {
    private Long id;
    private Long userId;
    private Long orderItemId;
    private ExperienceType type;
    private String title;
    private String slug;
    private String description;
    private boolean active;
    private Instant createdAt;
    private List<ExperienceLink> links;
}
