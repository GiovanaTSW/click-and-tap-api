package mx.edu.clickandtap.api.persistence.mapper;

import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserEntity entity);
    UserEntity toEntity(User user);
}