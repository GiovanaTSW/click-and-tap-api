package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class DigitalExperience {
    private Long id;
    private Long userId;
    private Long orderItemId;
    private ExperienceType type;
    private String title;
    private String slug;
    private String decription;
    private boolean active;
    private LocalDateTime createdAt;
    private List<ExperienceLink> links;
}
