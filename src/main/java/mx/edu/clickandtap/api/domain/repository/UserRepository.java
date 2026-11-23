package mx.edu.clickandtap.api.domain.repository;

import mx.edu.clickandtap.api.domain.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> getById(Long id);
    Optional<User> getByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
}
