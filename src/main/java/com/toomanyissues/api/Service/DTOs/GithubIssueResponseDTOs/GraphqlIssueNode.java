package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record GraphqlIssueNode(
        String id,
        String title,
        String body,
        String url,
        String createdAt,
        String updatedAt,
        GraphqlCountWrapper comments,
        IssueLabelConnection labels,
        GraphqlCountWrapper assignees
) {
    @Override
    public String toString() {
        return "GraphqlIssueNode{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", url='" + url + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", comments=" + comments +
                ", labels=" + labels +
                ", assignees=" + assignees +
                '}';
    }
}
