package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record IssueRepository(String nameWithOwner,String url,PrimaryLanguage primaryLanguage) {

    public String toString() {
        return "IssueRepository{" +
                "nameWithOwner='" + nameWithOwner + '\'' +
                ", url='" + url + '\'' +
                ", primaryLanguage='" + primaryLanguage + '\'' +
                '}';
    }
}
