package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record IssueAssigneeNode(String login, String url, String id) {
    @Override
    public String toString() {
        return "IssueAssigneeNode{" +
                "login='" + login + '\'' +
                ", url='" + url + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
