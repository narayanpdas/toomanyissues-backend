package com.toomanyissues.api.Model;


import com.toomanyissues.api.Controller.Reponses.GithubObjects.GithubIssueInfo;
import com.toomanyissues.api.Controller.Reponses.GithubObjects.GithubLabelInfo;
import com.toomanyissues.api.Controller.Reponses.GithubObjects.GithubUserInfo;
import com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs.GraphqlIssueNode;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Document(collection = "githubIssues")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "core_filter_idx", def = "{ 'isActive': 1, 'primaryLanguage': 1, 'labels.name': 1 }"),
        @CompoundIndex(name = "lang_only_idx", def = "{ 'isActive': 1, 'primaryLanguage': 1 }"),
        @CompoundIndex(name = "label_only_idx", def = "{ 'isActive': 1, 'labels.name': 1 }"),
        @CompoundIndex(name = "sorting_idx", def = "{ 'isActive': 1, 'createdAtGithub': -1 }")
})

public class GithubIssues {
    @Id private String githubIssueId;
    @TextIndexed(weight = 2)
    private String title;
    @TextIndexed
    private String body;
    private String htmlUrl;
    private List<GithubUserInfo> assignees = new ArrayList<>();
    private List<GithubLabelInfo> labels = new ArrayList<>();
    private Instant createdAtGithub;
    private Instant updatedAtGithub;
    private GithubUserInfo user;
    private Integer comments;
    private String repositoryUrl;
    private Instant scrapedAt;
    private Boolean isActive = Boolean.TRUE;
    private String primaryLanguage;
    private String summary=null;

    // Derived variables
    private String repoName;
    private String userName;
    private Integer assigneeCount;


    public GithubIssues(GithubIssueInfo githubIssueInfo) {
        this.githubIssueId = githubIssueInfo.node_id();
        this.title = githubIssueInfo.title();
        this.body = githubIssueInfo.body();
        this.htmlUrl = githubIssueInfo.html_url();
        this.createdAtGithub = Instant.parse(githubIssueInfo.created_at().toString());
        this.updatedAtGithub = Instant.parse(githubIssueInfo.updated_at().toString());
        this.labels = githubIssueInfo.labels();
        this.assignees = githubIssueInfo.assignees();
        this.user = githubIssueInfo.user();
        this.comments = githubIssueInfo.comments();
        this.repositoryUrl = githubIssueInfo.repository_url();

        this.scrapedAt = Instant.parse(LocalDateTime.now().toString());
        String[] repositoryUrlComponents = githubIssueInfo.repository_url().split("/");
        this.repoName = repositoryUrlComponents[repositoryUrlComponents.length - 1];
        this.userName = githubIssueInfo.user().login();
        this.assigneeCount = assignees.size();

    }
    public GithubIssues(GraphqlIssueNode graphqlNode) {
        this.githubIssueId = graphqlNode.id();
        this.title = graphqlNode.title();
        this.body = graphqlNode.body();
        this.htmlUrl = graphqlNode.url();

        this.createdAtGithub = Instant.parse(graphqlNode.createdAt());
        this.updatedAtGithub = Instant.parse(graphqlNode.updatedAt());



        this.user = new GithubUserInfo(null,"Ghost User",null);
        this.userName = "Ghost User";

        this.comments = (graphqlNode.comments() != null) ? graphqlNode.comments().totalCount() : 0;

        if (graphqlNode.labels() != null && graphqlNode.labels().nodes() != null) {
            this.labels = graphqlNode.labels().nodes().stream()
                    .map(l -> new GithubLabelInfo(l.name().toLowerCase(),
                            (l.color()!=null)?l.color().toLowerCase():null,
                            null))
                    .toList();
        } else {
            this.labels = new ArrayList<>();
        }
        this.assignees = new ArrayList<>();
        this.assigneeCount = graphqlNode.assignees().totalCount();
        this.scrapedAt = Instant.now();
        this.isActive = true;
    }
}