package com.toomanyissues.api.Controller.Reponses.GithubObjects;

import java.time.LocalDateTime;
import java.util.List;

public record GithubIssueInfo(
        String node_id,
        String title,
        String body,
        String html_url,
        String repository_url,
        String state,
        Integer comments,
        GithubUserInfo user,
        List<GithubUserInfo> assignees,
        List<GithubLabelInfo> labels,
        LocalDateTime created_at,
        LocalDateTime updated_at,
        GithubRepoInfo repository
)
{
    @Override
    public String toString() {
        return "GithubIssueInfo{" +
                "node_id='" + node_id + '\'' +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", html_url='" + html_url + '\'' +
                ", state='" + state + '\'' +
                ", created_at=" + created_at +
                ", updated_at=" + updated_at +
                ", repository=" + repository +
                '}';
    }
}
