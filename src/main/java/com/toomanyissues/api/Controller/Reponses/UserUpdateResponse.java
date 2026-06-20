package com.toomanyissues.api.Controller.Reponses;

import java.util.List;
import java.util.Set;

public record UserUpdateResponse(String name,
                                 String username,
                                 String email,
                                 Set<String> preferences,
                                 Set<String> primaryLanguages) {
}
