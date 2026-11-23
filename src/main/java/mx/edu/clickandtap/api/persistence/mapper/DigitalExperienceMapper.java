package mx.edu.clickandtap.api.persistence.mapper;

import mx.edu.clickandtap.api.domain.DigitalExperience;
import mx.edu.clickandtap.api.persistence.entity.DigitalExperienceEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring", uses = ExperienceLinkMapper.class)
public interface DigitalExperienceMapper {
    DigitalExperience toExperience(DigitalExperienceEntity entity);
    List<DigitalExperience> toExperiences(List<DigitalExperienceEntity> entities);
    DigitalExperienceEntity toEntity(DigitalExperience experience);

    @AfterMapping
    default void linkLinks(@MappingTarget DigitalExperienceEntity entity) {
        if (entity.getLinks() != null) entity.getLinks().forEach(l -> l.setExperience(entity));
    }
}