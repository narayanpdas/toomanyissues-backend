package com.toomanyissues.api.Model;

import com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs.GraphqlRepoNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

// TODO CHANGE THE NAME HERE
@Entity(name="scrapped_repo_info")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ScrappedRepoInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String repoName;
    private String repoOwnerName;
    private String repoType;
    private String licenseInfo;
    private boolean hasIssuesEnabled;
    private String primaryLanguage;
    private Long issueCount;
    private Long pullRequestCount;
    private String repoUrl;
    private Long startGazerCount;
    private Instant pushedAt;


    @Column(name = "raw_readme",columnDefinition = "TEXT")
    private String rawReadme=null;
    @Column(name = "ai_summary",columnDefinition = "TEXT")
    private String aiSummary=null;
    @Column(name = "ai_summary_status",length = 30)
    private String aiSummaryStatus="PENDING";
    @Column(name = "activity_temperature")
    private String activityTemperature = "WARM";

    private Instant lastIssueSync;
    @CreatedDate
    @Column(updatable = false)
    private Instant createdDate;
    @LastModifiedDate
    private Instant lastModifiedDate;

    public void fromRequest(GraphqlRepoNode node) {
        this.repoName = node.owner().login() + "/" + node.name();
        this.repoUrl = node.url();
        this.repoOwnerName = node.owner().login();
        this.startGazerCount = node.stargazerCount();
        this.hasIssuesEnabled = node.hasIssuesEnabled();
        if(node.licenseInfo() != null) {this.licenseInfo = node.licenseInfo().spdxId();}
        if(node.primaryLanguage() != null) {this.primaryLanguage=node.primaryLanguage().name();}
        if(node.issues() != null) {this.issueCount = node.issues().totalCount();}
        if(node.pullRequests() != null) {this.pullRequestCount = node.pullRequests().totalCount();}
        if (node.stargazerCount() >= 10000) {
            this.repoType = "ENTERPRISE";
        } else {
            this.repoType = "INDIE";
        }
        this.pushedAt = Instant.parse(node.pushedAt());
        this.lastIssueSync =Instant.EPOCH;
    }
}
