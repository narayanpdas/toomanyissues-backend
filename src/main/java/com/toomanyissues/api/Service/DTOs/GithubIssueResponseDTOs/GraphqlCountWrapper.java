package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record GraphqlCountWrapper(Integer totalCount) {
    @Override
    public String toString() {
        return "GraphqlCountWrapper{" +
                "totalCount=" + totalCount +
                '}';
    }
}
