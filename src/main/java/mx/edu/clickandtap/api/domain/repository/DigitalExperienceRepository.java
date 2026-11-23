package mx.edu.clickandtap.api.domain.repository;

import mx.edu.clickandtap.api.domain.DigitalExperience;
import java.util.List;
import java.util.Optional;

public interface DigitalExperienceRepository {
    List<DigitalExperience> getByUserId(Long userId);
    Optional<DigitalExperience> getById(Long id);
    Optional<DigitalExperience> getBySlug(String slug);
    boolean existsBySlug(String slug);
    DigitalExperience save(DigitalExperience experience);
    void delete(Long id);
}
