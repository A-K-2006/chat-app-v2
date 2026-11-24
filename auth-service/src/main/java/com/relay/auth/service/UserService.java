package com.relay.auth.service;

import com.relay.auth.dto.UserResponse;
import com.relay.auth.exception.ApiException;
import com.relay.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return users.findById(id)
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail()))
                .orElseThrow(() -> ApiException.notFound("user not found"));
    }
}
