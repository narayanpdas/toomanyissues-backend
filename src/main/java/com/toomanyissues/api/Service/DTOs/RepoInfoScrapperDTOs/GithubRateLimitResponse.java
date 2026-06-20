package com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubRateLimitResponse(
        Resources resources
) {
    public record Resources(
            GraphQL graphql
    ) {
    }

    public record GraphQL(
            int limit,
            int remaining,
            long reset
    ) {
    }
}
