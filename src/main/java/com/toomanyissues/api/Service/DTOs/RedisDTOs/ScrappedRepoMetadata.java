package com.toomanyissues.api.Service.DTOs.RedisDTOs;

import java.time.Instant;

public record ScrappedRepoMetadata(
        Long id,
        String repoOwnerName,
        String repoName,
        String repoUrl,
        String repoType,
        String primaryLanguage,
        Instant lastIssueSync,
        String activityTemperature
) {}