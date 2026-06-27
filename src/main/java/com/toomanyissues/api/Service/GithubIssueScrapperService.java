package com.toomanyissues.api.Service;

import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.Model.ScrapperMetrics;
import com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs.GraphqlIssueNode;
import com.toomanyissues.api.Service.DTOs.GithubGraphql.GraphqlAliasResponse;
import com.toomanyissues.api.Service.DTOs.GithubGraphql.AliasRepository;
import com.toomanyissues.api.Service.DTOs.RedisDTOs.ScrappedRepoMetadata;
import com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs.GithubRateLimitResponse;
import com.toomanyissues.api.repository.GithubIssuesRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
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
    private final RedisService redisService;
    private final SchedulerManagerService schedulerManagerService;
    private final Random random = new Random();
    private final ExecutorService githubExecutor = Executors.newFixedThreadPool(3);

    public GithubIssueScrapperService(
            GithubIssuesRepository githubIssuesRepository,
            SchedulerManagerService schedulerManagerService,
            RedisService redisService,
            @Value("${app.githubPAT}") String appGithubPat
    ) {
        this.githubIssuesRepository = githubIssuesRepository;
        this.schedulerManagerService = schedulerManagerService;
        this.redisService = redisService;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("User-Agent", "Mr-Aggregator")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("Authorization", "Bearer " + appGithubPat)
                .build();
    }

    @Scheduled(fixedDelay = 3600000) // 1 hour
    public void scrapHotRepoIssues() {
        processRepositoriesInChunks("HOT", "issue-scraper-hot");
    }
    @Scheduled(fixedDelay = 14400000, initialDelay = 900000) // 4 hours // Initial delay of 15 mins
    public void scrapWarmRepoIssues() {
        processRepositoriesInChunks("WARM", "issue-scraper-warm");
    }
    @Scheduled(fixedDelay = 86400000, initialDelay = 1800000) // 24 hours // Initial delay of 30 mins
    public void scrapColdRepoIssues() {
        processRepositoriesInChunks("COLD", "issue-scraper-cold");
    }
    // ==========================================
    // ORCHESTRATOR METHOD
    // ==========================================
    private void processRepositoriesInChunks(String repoTemperature, String metricKey) {
        ScrapperMetrics metrics = schedulerManagerService.getMetrics(metricKey);

        if ("PAUSED".equals(metrics.getStatus().get())) {
            System.out.println("[JOB: " + metricKey + "] [PAUSED]");
            return;
        }
        try {
            int initialRateLimit = fetchCurrentRateLimit();

            // 1. Fetch exactly which IDs belong to this temperature bucket right now
            List<Long> rawRepoIds = redisService.getRepoIdsByTemperature(repoTemperature);

            // Convert Long IDs to Strings for the Redis Hash fetch
            List<String> repoIds = rawRepoIds.stream().map(String::valueOf).toList();

            initializeMetrics(metrics, repoTemperature, metricKey, repoIds.size());

            // 2. Fetch the actual Hash Data from Redis in a single Pipelined Batch
            List<Map<String, String>> repoDataHashes = redisService.getBatchRepoData(repoIds);

            // 3. Map the raw Redis Hashes into our clean RepoContext DTOs
            List<ScrappedRepoMetadata> reposToScrape = getRepoContexts(repoIds, repoDataHashes);

            if (reposToScrape.isEmpty()) {
                finalizeMetrics(metrics, initialRateLimit);
                return;
            }

            // 4. Send the context list into the Async Executor
            boolean continueProcessing = executeAsyncChunks(reposToScrape, metrics, metricKey);

            if (continueProcessing) {
                metrics.getProgress().addAndGet(reposToScrape.size());
            }

            finalizeMetrics(metrics, initialRateLimit);
            if ("PAUSED".equals(metrics.getStatus().get())) {
                System.out.println("[JOB: " + metricKey + "] [PAUSED]");
                return;
            }
        }
        catch (Exception e) {
            System.out.println("[JOB: " + metricKey + "] [FAILED]"+"\nERROR: "+e.getMessage());
        }
    }

    private static @NonNull List<ScrappedRepoMetadata> getRepoContexts(List<String> repoIds,
                                                                       List<Map<String, String>> repoDataHashes) {
        List<ScrappedRepoMetadata> reposToScrape = new ArrayList<>();
        for (int i = 0; i < repoIds.size(); i++) {
            Map<String, String> data = repoDataHashes.get(i);
            if (data != null && !data.isEmpty()) {
                reposToScrape.add(new ScrappedRepoMetadata(
                        Long.parseLong(repoIds.get(i)),
                        data.get("repoOwnerName"),
                        data.get("repoName"),
                        data.get("repoUrl"),
                        data.get("repoType"),
                        data.get("primaryLanguage"),
                        Instant.parse(data.get("lastIssueSync")),
                        data.get("activityTemperature")
                ));
            }
        }
        return reposToScrape;
    }

    // ==========================================
    // ASYNC EXECUTION
    // ==========================================
    private boolean executeAsyncChunks(List<ScrappedRepoMetadata> reposToScrape, ScrapperMetrics metrics, String metricKey) {
        List<List<ScrappedRepoMetadata>> graphqlChunks = partitionList(reposToScrape, 5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<ScrappedRepoMetadata> chunk : graphqlChunks) {
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
    // GRAPHQL PIPELINE
    // ==========================================
    private void executeGraphqlSearch(List<ScrappedRepoMetadata> repos, ScrapperMetrics metrics) {
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

    private String buildGraphqlQuery(List<ScrappedRepoMetadata> repos) {
        StringBuilder queryBuilder = new StringBuilder("query {\n");
        Instant now = Instant.now();

        for (int i = 0; i < repos.size(); i++) {
            ScrappedRepoMetadata repo = repos.get(i);
            String owner = repo.repoOwnerName();
            String name = repo.repoName().contains("/") ? repo.repoName().split("/")[1] : repo.repoName();

            Instant lastSync = repo.lastIssueSync() != null ? repo.lastIssueSync() : now.minus(7, ChronoUnit.DAYS);
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

    private void processGraphqlResponse(List<ScrappedRepoMetadata> repos,
                                        GraphqlAliasResponse responses,
                                        ScrapperMetrics metrics,
                                        long startTime
    ) {
        List<GithubIssues> issuesToSave = new ArrayList<>();

        for (int i = 0; i < repos.size(); i++) {
            ScrappedRepoMetadata localRepoData = repos.get(i);
            AliasRepository aliasRepo = responses.data().get("repo" + i);
            int newIssuesFound = 0;

            if (aliasRepo != null && aliasRepo.issues() != null) {
                newIssuesFound = aliasRepo.issues().nodes().size();
                for (GraphqlIssueNode node : aliasRepo.issues().nodes()) {
                    GithubIssues issue = new GithubIssues(node);
                    issue.setRepoName(localRepoData.repoName());
                    issue.setRepositoryUrl(localRepoData.repoUrl());
                    issue.setPrimaryLanguage(localRepoData.primaryLanguage());

                    boolean hasLabels = issue.getLabels() != null && !issue.getLabels().isEmpty();
                    boolean hasValidTitle = issue.getTitle() != null && issue.getTitle().trim().length() > 8;
                    boolean hasValidBody = issue.getBody() != null && issue.getBody().trim().length() > 15;

                    if (hasLabels && hasValidTitle && hasValidBody) issuesToSave.add(issue);
                }
            }

            String repoTemp = "";
            if (newIssuesFound >= 5) repoTemp = "HOT";
            else if (newIssuesFound > 0) repoTemp = "WARM";
            else repoTemp="COLD";
            // UPDATE required for next pulling.
            redisService.setRepoByTemperature(
                    localRepoData.id().toString(),
                    localRepoData.activityTemperature(),
                    repoTemp
            );
            // UPDATE required for next push to SupaBase.
            redisService.updateRepoTemperatureAndSyncTime(
                    localRepoData.id().toString(),
                    repoTemp,
                    Instant.now().toString()
            );
            redisService.markRepoAsDirty(localRepoData.id().toString());
        }
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

    private void initializeMetrics(ScrapperMetrics metrics, String repoTemperature, String metricKey,int total) {
        metrics.getStatus().set("RUNNING");
        metrics.getPointsCostInCurrentCycle().set(0);
        metrics.getProgress().set(0);
        metrics.getTotal().set(total);
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