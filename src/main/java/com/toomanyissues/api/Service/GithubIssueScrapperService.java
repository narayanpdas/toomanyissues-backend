package com.toomanyissues.api.Service;

import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.Model.ScrapperMetrics;
import com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs.GraphqlIssueNode;
import com.toomanyissues.api.Service.DTOs.GithubGraphql.GraphqlAliasResponse;
import com.toomanyissues.api.Service.DTOs.GithubGraphql.AliasRepository;
import com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs.GithubRateLimitResponse;
import com.toomanyissues.api.repository.GithubIssuesRepository;
import com.toomanyissues.api.repository.ScrappedRepoInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class GithubIssueScrapperService {

    private final RestClient restClient;
    private final GithubIssuesRepository githubIssuesRepository;
    private final ScrappedRepoInfoRepository scrappedRepoInfoRepository;
    private final SchedulerManagerService schedulerManagerService;
    private final Random random = new Random();
    private final ExecutorService githubExecutor = Executors.newFixedThreadPool(3);

    public GithubIssueScrapperService(
            GithubIssuesRepository githubIssuesRepository,
            ScrappedRepoInfoRepository scrappedRepoInfoRepository,
            SchedulerManagerService schedulerManagerService,
            @Value("${app.githubPAT}") String appGithubPat
    ) {
        this.githubIssuesRepository = githubIssuesRepository;
        this.scrappedRepoInfoRepository = scrappedRepoInfoRepository;
        this.schedulerManagerService = schedulerManagerService;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("User-Agent", "Mr-Aggregator")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("Authorization", "Bearer " + appGithubPat)
                .build();
    }

    @Scheduled(fixedDelay = 3600000) // 1 hour
    public void scrapBigRepoIssues() {
        processRepositoriesInChunks("ENTERPRISE", "issue-scraper-enterprise");
    }

    @Scheduled(fixedDelay = 7200000, initialDelay = 900000) // 2 hours // Initial delay of 15 mins
    public void scrapSmallRepoIssues() {
        processRepositoriesInChunks("INDIE", "issue-scraper-indie");
    }

    // ==========================================
    // ORCHESTRATOR METHOD (Reads like a story)
    // ==========================================
    private void processRepositoriesInChunks(String repoType, String metricKey) {
        ScrapperMetrics metrics = schedulerManagerService.getMetrics(metricKey);

        if (metrics.getStatus().get().equals("PAUSED")) {
            System.out.println("[JOB: " + metricKey + "] [PAUSED]");
            return;
        }

        int initialRateLimit = fetchCurrentRateLimit();
        initializeMetrics(metrics, repoType, metricKey);

        int pageNumber = 0;
        int pageSize = 100;
        Page<ScrappedRepoInfo> repoPage = scrappedRepoInfoRepository.findByRepoType(repoType, PageRequest.of(pageNumber, pageSize));

        while (repoPage.hasNext()) {
            List<ScrappedRepoInfo> repoChunkList = repoPage.getContent();
            List<ScrappedRepoInfo> reposToScrape = filterEligibleRepos(repoChunkList); // The Bouncer

            if (reposToScrape.isEmpty()) {
                metrics.getProgress().addAndGet(repoChunkList.size());
                repoPage = scrappedRepoInfoRepository.findByRepoType(repoType, PageRequest.of(++pageNumber, pageSize));
                continue;
            }

            boolean continueProcessing = executeAsyncChunks(reposToScrape, metrics, metricKey);
            if (!continueProcessing) {
                finalizeMetrics(metrics, initialRateLimit);
                return;
            }

            metrics.getProgress().addAndGet(repoChunkList.size());
            sleepWithJitter();
            repoPage = scrappedRepoInfoRepository.findByRepoType(repoType, PageRequest.of(++pageNumber, pageSize));
        }

        finalizeMetrics(metrics, initialRateLimit);
    }

    // ==========================================
    // ASYNC EXECUTION
    // ==========================================
    private boolean executeAsyncChunks(List<ScrappedRepoInfo> reposToScrape, ScrapperMetrics metrics, String metricKey) {
        List<List<ScrappedRepoInfo>> graphqlChunks = partitionList(reposToScrape, 5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<ScrappedRepoInfo> chunk : graphqlChunks) {
            if (metrics.getStatus().get().equals("PAUSED")) {
                System.out.println("[JOB: " + metricKey + "] [Safely Processed and PAUSED]");
                return false;
            }

            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> executeGraphqlSearch(chunk, metrics),
                    githubExecutor
            );
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allFutures.join();
        } catch (Exception e) {
            System.err.println("⚠️ Error waiting for async requests: " + e.getMessage());
        }
        return true;
    }

    // ==========================================
    // THE BOUNCER (Rate Limit Optimization)
    // ==========================================
    private List<ScrappedRepoInfo> filterEligibleRepos(List<ScrappedRepoInfo> repoChunkList) {
        List<ScrappedRepoInfo> reposToScrape = new ArrayList<>();
        Instant now = Instant.now();

        for (ScrappedRepoInfo repo : repoChunkList) {
            Instant lastSync = repo.getLastIssueSync() != null ? repo.getLastIssueSync() : now.minus(7, ChronoUnit.DAYS);
            long hoursSinceLastSync = ChronoUnit.HOURS.between(lastSync, now);
            String temp = repo.getActivityTemperature() != null ? repo.getActivityTemperature() : "WARM";

            if (temp.equals("HOT") && hoursSinceLastSync >= 1) reposToScrape.add(repo);
            else if (temp.equals("WARM") && hoursSinceLastSync >= 4) reposToScrape.add(repo);
            else if (temp.equals("COLD") && hoursSinceLastSync >= 24) reposToScrape.add(repo);
        }
        return reposToScrape;
    }

    // ==========================================
    // GRAPHQL PIPELINE
    // ==========================================
    private void executeGraphqlSearch(List<ScrappedRepoInfo> repos, ScrapperMetrics metrics) {
        long startTime = System.currentTimeMillis();
        try {
            String graphqlQuery = buildGraphqlQuery(repos); // The Time Clamp

            GraphqlAliasResponse responses = restClient.post()
                    .uri("/graphql")
                    .body(Map.of("query", graphqlQuery))
                    .retrieve()
                    .body(GraphqlAliasResponse.class);

            if (responses != null && responses.errors() != null && !responses.errors().isEmpty()) {
                System.out.println("⚠️ GraphQL Partial Warning: " + responses.errors().get(0).message());
            }

            if (responses != null && responses.data() != null) {
                processGraphqlResponse(repos, responses, metrics, startTime);
            }
        } catch (Exception e) {
            System.out.println("❌ GraphQL V2 Sync Failed: " + e.getMessage());
        }
    }

    private String buildGraphqlQuery(List<ScrappedRepoInfo> repos) {
        StringBuilder queryBuilder = new StringBuilder("query {\n");
        Instant now = Instant.now();

        for (int i = 0; i < repos.size(); i++) {
            ScrappedRepoInfo repo = repos.get(i);
            String owner = repo.getRepoOwnerName();
            String name = repo.getRepoName().contains("/") ? repo.getRepoName().split("/")[1] : repo.getRepoName();

            Instant lastSync = repo.getLastIssueSync() != null ? repo.getLastIssueSync() : now.minus(7, ChronoUnit.DAYS);
            if (lastSync.isBefore(now.minus(7, ChronoUnit.DAYS))) {
                lastSync = now.minus(7, ChronoUnit.DAYS);
            }

            String safeSinceIsoString = lastSync.toString();
            queryBuilder.append("  repo").append(i).append(": repository(owner: \"").append(owner).append("\", name: \"").append(name).append("\") {\n")
                    .append("    issues(states: OPEN, first: 5, orderBy: {field: UPDATED_AT, direction: DESC}, filterBy: {since: \"").append(safeSinceIsoString).append("\"}) {\n")
                    .append("      nodes { id title body url createdAt updatedAt comments { totalCount } assignees { totalCount } labels(first: 10) { nodes { name color } } }\n")
                    .append("    }\n  }\n");
        }
        queryBuilder.append("}");
        return queryBuilder.toString();
    }

    private void processGraphqlResponse(List<ScrappedRepoInfo> repos, GraphqlAliasResponse responses, ScrapperMetrics metrics, long startTime) {
        List<GithubIssues> issuesToSave = new ArrayList<>();
        List<ScrappedRepoInfo> reposToUpdate = new ArrayList<>();

        for (int i = 0; i < repos.size(); i++) {
            ScrappedRepoInfo localRepoData = repos.get(i);
            AliasRepository aliasRepo = responses.data().get("repo" + i);
            int newIssuesFound = 0;

            if (aliasRepo != null && aliasRepo.issues() != null) {
                newIssuesFound = aliasRepo.issues().nodes().size();
                for (GraphqlIssueNode node : aliasRepo.issues().nodes()) {
                    GithubIssues issue = new GithubIssues(node);
                    issue.setRepoName(localRepoData.getRepoName());
                    issue.setRepositoryUrl(localRepoData.getRepoUrl());
                    issue.setPrimaryLanguage(localRepoData.getPrimaryLanguage());

                    boolean hasLabels = issue.getLabels() != null && !issue.getLabels().isEmpty();
                    boolean hasValidTitle = issue.getTitle() != null && issue.getTitle().trim().length() > 8;
                    boolean hasValidBody = issue.getBody() != null && issue.getBody().trim().length() > 15;

                    if (hasLabels && hasValidTitle && hasValidBody) issuesToSave.add(issue);
                }
            }


            if (newIssuesFound >= 5) localRepoData.setActivityTemperature("HOT");
            else if (newIssuesFound > 0) localRepoData.setActivityTemperature("WARM");
            else localRepoData.setActivityTemperature("COLD");

            localRepoData.setLastIssueSync(Instant.now());
            reposToUpdate.add(localRepoData);
        }


        if (!reposToUpdate.isEmpty()) scrappedRepoInfoRepository.saveAll(reposToUpdate);

        long executionTime = System.currentTimeMillis() - startTime;
        if (!issuesToSave.isEmpty()) {
            githubIssuesRepository.saveAll(issuesToSave);
            metrics.getCount().addAndGet(issuesToSave.size());
            System.out.println("[GITHUB V2 BATCH] Saved " + issuesToSave.size() + " active issues across chunk in " + executionTime + "ms");
        } else {
            System.out.println("[GITHUB V2 BATCH] 0 updates found across current chunk - " + executionTime + "ms");
        }
    }

    // ==========================================
    // METRICS & UTILS
    // ==========================================
    private int fetchCurrentRateLimit() {
        GithubRateLimitResponse rate = restClient.get()
                .uri("/rate_limit")
                .retrieve()
                .body(GithubRateLimitResponse.class);
        return rate != null ? rate.resources().graphql().remaining() : 0;
    }

    private void initializeMetrics(ScrapperMetrics metrics, String repoType, String metricKey) {
        metrics.getStatus().set("RUNNING");
        metrics.getPointsCostInCurrentCycle().set(0);
        metrics.getProgress().set(0);
        metrics.getTotal().set((int) scrappedRepoInfoRepository.countByRepoType(repoType));
        System.out.println("[JOB: " + metricKey + "] [STARTED]");
    }

    private void finalizeMetrics(ScrapperMetrics metrics, int initialRateLimit) {
        int finalRateLimit = fetchCurrentRateLimit();
        if (initialRateLimit > 0 && finalRateLimit > 0) {
            int pointsCost = initialRateLimit - finalRateLimit;
            System.out.println("Points cost in this Cycle: " + pointsCost);
            metrics.getPointsCostInCurrentCycle().set(pointsCost);
            metrics.getTotalPointsCost().addAndGet(pointsCost);
        }
        metrics.getStatus().set("IDLE");
    }

    private void sleepWithJitter() {
        try {
            Thread.sleep(1500 + random.nextInt(1500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}