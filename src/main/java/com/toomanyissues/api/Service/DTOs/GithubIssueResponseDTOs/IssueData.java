package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;
public record IssueData(IssueSearch search) {
    @Override
    public String toString() {
        return "IssueData{" +
                "search=" + search +
                '}';
    }
}