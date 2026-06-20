package com.toomanyissues.api.Controller.Reponses;

import java.time.Instant;

public record RefreshToken(
        String token,
        Instant expirationTime
) {
}
