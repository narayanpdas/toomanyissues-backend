package com.toomanyissues.api.Controller.Reponses;

import java.time.Instant;

public record UserResponse(
        String username,
        String jwtToken,
        String refreshToken,
        Instant jwtExpiration,
        Instant refreshTokenExpiration,
        String message,
        String role
) {
}
