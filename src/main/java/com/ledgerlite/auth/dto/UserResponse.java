package com.ledgerlite.auth.dto;

import com.ledgerlite.user.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, Role role) {}
