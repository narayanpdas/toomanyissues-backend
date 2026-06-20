package com.toomanyissues.api.Controller.Requests;

import jakarta.validation.constraints.NotBlank;

public record UserLoginCredentials(
        @NotBlank(message = "Username required") String username,
        @NotBlank(message = "Password required") String password) {
}
