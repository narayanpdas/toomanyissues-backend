package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record IssueAuthor(String login, String url) {
    @Override
    public String toString() {
        return "IssueAuthor{" +
                "login='" + login + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
