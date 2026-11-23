package mx.edu.clickandtap.api.persistence;

import lombok.RequiredArgsConstructor;
import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.domain.repository.UserRepository;
import mx.edu.clickandtap.api.persistence.crud.UserCrudRepository;
import mx.edu.clickandtap.api.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserCrudRepository crud;
    private final UserMapper mapper;

    @Override
    public Optional<User> getById(Long id) { return crud.findById(id).map(mapper::toUser); }

    @Override
    public Optional<User> getByEmail(String email) { return crud.findByEmail(email).map(mapper::toUser); }

    @Override
    public boolean existsByEmail(String email) { return crud.existsByEmail(email); }

    @Override
    public User save(User user) { return mapper.toUser(crud.save(mapper.toEntity(user))); }
}