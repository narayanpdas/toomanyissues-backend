package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

import java.util.List;

public record IssueSearch(List<GraphqlIssueNode> nodes) {
    @Override
    public String toString() {
        return "IssueSearch{" +
                "nodes=" + nodes +
                '}';
    }
}

