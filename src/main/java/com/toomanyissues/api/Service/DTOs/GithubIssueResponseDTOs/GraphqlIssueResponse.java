package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

import java.util.List;

public record GraphqlIssueResponse(IssueData data,List<GraphqlError> errors) {
    @Override
    public String toString() {
        return "GraphqlIssueResponse{" +
                "data=" + data +
                ", errors=" + errors +
                '}';
    }
}

