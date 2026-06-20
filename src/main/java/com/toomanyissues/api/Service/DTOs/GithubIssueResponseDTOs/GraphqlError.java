package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record GraphqlError(String message) {
    @Override
    public String toString() {
        return "GraphqlError{" +
                "message='" + message + '\'' +
                '}';
    }
}
