package com.ledgerlite.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** Deliberately has no password hash and no token: there is nothing sensitive to leak from this type. */
public record AdminUserResponse(UUID id, String email, String role, Instant createdAt) {}
