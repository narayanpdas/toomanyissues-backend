package com.toomanyissues.api.Controller.Reponses.AdminResponses;



import java.util.Map;

public record UserStatusResponse(
        Long totalUsers,
        Map<String,Long> byLanguage,
        Map<String,Long> byLabel
) {
}
