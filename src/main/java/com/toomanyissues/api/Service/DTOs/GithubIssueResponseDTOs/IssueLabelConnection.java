package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

import java.util.List;

public record IssueLabelConnection(List<IssueLabelNode> nodes) {
    @Override
    public String toString() {
        return "IssueLabelConnection{" +
                "nodes=" + nodes +
                '}';
    }
}
