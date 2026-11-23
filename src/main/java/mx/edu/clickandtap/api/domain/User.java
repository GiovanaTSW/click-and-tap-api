package mx.edu.clickandtap.api.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class User {
    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String phone;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;
}
