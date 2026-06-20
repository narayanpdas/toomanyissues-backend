package com.toomanyissues.api.Controller.Reponses.AdminResponses;

public record GithubApiStatusResponse(
        Integer remainingPoints,
        String resetTime
) {
}
