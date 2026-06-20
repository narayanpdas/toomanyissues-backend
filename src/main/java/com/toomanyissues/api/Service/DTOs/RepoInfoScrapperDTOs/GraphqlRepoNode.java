package com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs;

public record GraphqlRepoNode(
        String name,
        RepoOwner owner,
        LicenseInfo licenseInfo,
        boolean hasIssuesEnabled,
        PrimaryLanguage primaryLanguage,
        Issues issues,
        PullRequests pullRequests,
        String url,
        Long stargazerCount,
        String pushedAt
) {}
