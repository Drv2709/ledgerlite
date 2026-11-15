package com.ledgerlite.transfer.dto;

import jakarta.validation.constraints.Size;

public record ReverseRequest(@Size(max = 200) String reason) {}
