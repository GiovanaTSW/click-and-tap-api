package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.domain.repository.DigitalExperienceRepository;
import mx.edu.clickandtap.api.persistence.crud.DigitalExperienceCrudRepository;
import mx.edu.clickandtap.api.persistence.mapper.DigitalExperienceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

/** Links are a lazy collection: mapping to domain objects must happen inside a transaction. */
@Repository
@RequiredArgsConstructor
public class DigitalExperienceRepositoryImpl implements DigitalExperienceRepository {
    private final DigitalExperienceCrudRepository crud;
    private final DigitalExperienceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<DigitalExperience> getByUserId(Long userId) { return mapper.toExperiences(crud.findByUserId(userId)); }

    @Override
    @Transactional(readOnly = true)
    public Optional<DigitalExperience> getById(Long id) { return crud.findById(id).map(mapper::toExperience); }

    @Override
    @Transactional(readOnly = true)
    public Optional<DigitalExperience> getBySlug(String slug) { return crud.findBySlug(slug).map(mapper::toExperience); }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) { return crud.existsBySlug(slug); }

    @Override
    @Transactional
    public DigitalExperience save(DigitalExperience experience) {
        return mapper.toExperience(crud.save(mapper.toEntity(experience)));
    }

    @Override
    @Transactional
    public void delete(Long id) { crud.deleteById(id); }
}
