package com.toomanyissues.api.Controller.Reponses;

import java.util.List;
import java.util.Set;

public record UserDetailsResponse (
        String name,
        String username,
        String email,
        Set<String> preferences,
        Set<String> primaryLanguages
){
    public UserDetailsResponse{
        if(name == null || email == null || username == null){
            throw new RuntimeException("Username or email or name cannot be null");
        }
    }
}
