package com.toomanyissues.api.Controller.Requests;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record UserUpdateRequest (
    String name ,
    @Size(min = 8,message = "Password must be at least 8 characters.")
    String password ,
    Set<String> preferences,
    Set<String> primaryLanguages
){}
