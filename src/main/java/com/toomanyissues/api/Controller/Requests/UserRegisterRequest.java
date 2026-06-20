package com.toomanyissues.api.Controller.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record UserRegisterRequest(
        // Required Fields
        @NotBlank(message = "Name cannot be blank")
        String name ,
        @NotBlank(message = "Username cannot be blank")
        String username ,
        @NotBlank(message = "Password is required")
        @Size(min = 8,message = "Password must be at least 8 characters.")
        String password ,
        @NotBlank(message = "email is required")
        String email ,
        Set<String> preferences,
        Set<String> primaryLanguages
) {
}
