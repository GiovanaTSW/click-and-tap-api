package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExperienceLink {
    private Long id;
    private String label;
    private String url;
    private String icon;
    private Integer position;
    private boolean active;
}
