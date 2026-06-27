package com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs;

public record RepoReadmeDTO(
        String repoName,
        String rawReadme
) {}