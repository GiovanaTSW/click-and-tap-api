package mx.edu.clickandtap.api.persistence.mapper;

import mx.edu.clickandtap.api.domain.ExperienceLink;
import mx.edu.clickandtap.api.persistence.entity.ExperienceLinkEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExperienceLinkMapper {
    ExperienceLink toLink(ExperienceLinkEntity entity);

    @Mapping(target = "experience", ignore = true)
    ExperienceLinkEntity toEntity(ExperienceLink link);
}