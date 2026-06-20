package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

import java.util.List;

public record IssueAssigneeConnection(List<IssueAssigneeNode> nodes) {
    @Override
    public String toString() {
        return "IssueAssigneeConnection{" +
                "nodes=" + nodes +
                '}';
    }
}
