package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.domain.repository.DigitalExperienceRepository;
import mx.edu.clickandtap.api.persistence.crud.DigitalExperienceCrudRepository;
import mx.edu.clickandtap.api.persistence.mapper.DigitalExperienceMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DigitalExperienceRepositoryImpl implements DigitalExperienceRepository {
    private final DigitalExperienceCrudRepository crud;
    private final DigitalExperienceMapper mapper;

    @Override
    public List<DigitalExperience> getByUserId(Long userId) { return mapper.toExperiences(crud.findByUserId(userId)); }

    @Override
    public Optional<DigitalExperience> getById(Long id) { return crud.findById(id).map(mapper::toExperience); }

    @Override
    public Optional<DigitalExperience> getBySlug(String slug) { return crud.findBySlug(slug).map(mapper::toExperience); }

    @Override
    public boolean existsBySlug(String slug) { return crud.existsBySlug(slug); }

    @Override
    public DigitalExperience save(DigitalExperience experience) {
        return mapper.toExperience(crud.save(mapper.toEntity(experience)));
    }

    @Override
    public void delete(Long id) { crud.deleteById(id); }
}