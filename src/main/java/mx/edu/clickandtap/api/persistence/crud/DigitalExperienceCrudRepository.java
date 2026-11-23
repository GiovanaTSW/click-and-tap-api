package mx.edu.clickandtap.api.persistence.crud;

import mx.edu.clickandtap.api.persistence.entity.DigitalExperienceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DigitalExperienceCrudRepository extends JpaRepository<DigitalExperienceEntity, Long> {
    Optional<DigitalExperienceEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<DigitalExperienceEntity> findByUserId(Long userId);
}