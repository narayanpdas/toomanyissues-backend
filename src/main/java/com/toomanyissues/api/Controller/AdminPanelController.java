package com.toomanyissues.api.Controller;


import com.toomanyissues.api.Controller.Reponses.AdminResponses.GithubApiStatusResponse;
import com.toomanyissues.api.Controller.Reponses.AdminResponses.IssueStatusResponse;

import com.toomanyissues.api.Controller.Reponses.AdminResponses.UserStatusResponse;
import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.ScrapperMetrics;

import com.toomanyissues.api.Service.GithubIssueScrapperService;
import com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs.GithubRateLimitResponse;
import com.toomanyissues.api.Service.SchedulerManagerService;
import com.toomanyissues.api.repository.GithubIssuesRepository;
import com.toomanyissues.api.repository.UserRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public class AdminPanelController {
    SchedulerManagerService schedulerManagerService;
    GithubIssueScrapperService githubIssueScrapperService;
    UserRepository userRepository;
    GithubIssuesRepository githubIssuesRepository;
    MongoTemplate mongoTemplate;
    private final RestClient restClient;

    public AdminPanelController(SchedulerManagerService schedulerManagerService,
                                GithubIssueScrapperService githubIssueScrapperService,
                                UserRepository userRepository,
                                GithubIssuesRepository githubIssuesRepository,
                                MongoTemplate mongoTemplate,
                                @Value("${app.githubPAT}") String appGithubPat
    ) {
        this.schedulerManagerService = schedulerManagerService;
        this.githubIssueScrapperService = githubIssueScrapperService;
        this.userRepository = userRepository;
        this.githubIssuesRepository = githubIssuesRepository;
        this.mongoTemplate = mongoTemplate;
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory)
                .baseUrl("https://api.github.com")
                .defaultHeader("User-Agent","Mr-Aggregator")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("Authorization","Bearer "+appGithubPat)
                .build();
    }

    @GetMapping("/admin/users/status")
    public ResponseEntity<UserStatusResponse> getUserStatus() {
        long userCount = userRepository.count();

        Map<String,Long> topLanguages = convertToMap(userRepository
                .findTopLanguages(PageRequest.of(0, 5)));

        Map<String,Long> topPreferences = convertToMap(userRepository
                .findTopPreferences(PageRequest.of(0, 5)));

        return ResponseEntity.ok(new UserStatusResponse(
                userCount,
                topLanguages,
                topPreferences
        ));
    }
    @GetMapping("/admin/github-api/status")
    public ResponseEntity<GithubApiStatusResponse> getGithubApiStatus() {
        try {
            GithubRateLimitResponse response = restClient.get()
                    .uri("/rate_limit")
                    .retrieve()
                    .body(GithubRateLimitResponse.class);

            if (response != null && response.resources() != null) {
                int remaining = response.resources().graphql().remaining();
                long resetTimestamp = response.resources().graphql().reset();
                String resetTime = java.time.Instant.ofEpochSecond(resetTimestamp).toString();

                return ResponseEntity.ok(new GithubApiStatusResponse(remaining, resetTime));
            }
            return ResponseEntity.ok(new GithubApiStatusResponse(0, "unknown"));
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch GitHub rate limit: " + e.getMessage());
            return ResponseEntity.ok(new GithubApiStatusResponse(0, "error"));
        }
    }
    @GetMapping("/admin/issues/status")
    public ResponseEntity<IssueStatusResponse>  getIssueStatus() {

        long totalIssues = githubIssuesRepository.count();

        Query query = new Query(Criteria.where("createdAtGithub").gte(LocalDateTime.now().minusHours(24)));
        long scrappedLastest = mongoTemplate.count(query, GithubIssues.class);
        Map<String,Long> topPrimaryLanguage = getTopMongoMetrics("primaryLanguage");
        Map<String,Long> topRepoName = getTopMongoMetrics("repoName");
        Map<String,Long> topLabelMetrics = getTopMongoLabelMetrics();
        return ResponseEntity.ok(new IssueStatusResponse(
                totalIssues,
                scrappedLastest,
                totalIssues -  scrappedLastest,
                topPrimaryLanguage,
                topRepoName,
                topLabelMetrics
        ));
    }
    private Map<String, Long> getTopMongoLabelMetrics() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.unwind("labels"),
                Aggregation.group("labels.name").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(5)
        );
        return executeAggregation(agg);
    }
    private Map<String, Long> getTopMongoMetrics(String fieldName) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.group(fieldName).count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(5)
        );
        return executeAggregation(agg);
    }
    private Map<String, Long> executeAggregation(Aggregation agg) {
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "githubIssues", Document.class);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Document doc : results.getMappedResults()) {
            String key = doc.getString("_id");
            if (key != null) map.put(key, doc.getInteger("count").longValue());
        }
        return map;
    }

    @GetMapping("/admin/schedulers/status")
    public ResponseEntity<Collection<ScrapperMetrics>> getSchedulerStatus() {
        return ResponseEntity.ok(schedulerManagerService.getAllMetrics());
    }
    @PostMapping("/admin/schedulers/{id}/{action}")
    public ResponseEntity<?> schedulerAction(
            @PathVariable String id,
            @PathVariable String action
    ){
        ScrapperMetrics metrics = schedulerManagerService.getMetrics(id);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        String currentStatus = metrics.getStatus().get();
        if (action.equals("PAUSED")) {
            metrics.getStatus().set("PAUSED");
            return ResponseEntity.ok().build();
        }
        if (action.equals("RUNNING")) {
            if (currentStatus.equals("RUNNING")) {
                return ResponseEntity.ok().build();
            }
            if (currentStatus.equals("PAUSED")) {
                metrics.getStatus().set("IDLE");
            }
        }
        return ResponseEntity.ok().build();
    }

    private Map<String, Long> convertToMap(List<Object[]> results) {
        return results.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
        ));
    }
}
