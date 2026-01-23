package com.optima.mapper;

import com.optima.entity.UserEntity;
import com.optima.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * User mappings.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {
    /**
     * Map user from api model to internal user entity.
     *
     * @param user user to convert
     * @return converted entity
     */
    @Mapping(target = "id", ignore = true)
    UserEntity convertToUserEntity(User user);

    /**
     * Map internal user entity to user api model.
     *
     * @param userEntity entity to convert
     * @return converted user
     */
    User convertToUser(UserEntity userEntity);
}
