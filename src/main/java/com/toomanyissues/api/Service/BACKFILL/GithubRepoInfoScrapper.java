package com.toomanyissues.api.Service.BACKFILL;

import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.Model.ScrapperMetrics;
import com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs.GithubGraphqlResponse;
import com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs.GraphqlRepoNode;
import com.toomanyissues.api.Service.SchedulerManagerService;
import com.toomanyissues.api.repository.ScrappedRepoInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GithubRepoInfoScrapper {
    private final ScrappedRepoInfoRepository scrappedRepoInfoRepository;
    private final RestClient restClient;
    private final SchedulerManagerService schedulerManagerService;
    private final ObjectMapper objectMapper;
    private static final LocalDate START_DATE = LocalDate.of(2026, 6, 10);
    private static final String SEARCH_GRAPHQL_QUERY = """
            query SearchRepos($queryString: String!, $afterCursor: String) {
              search(query: $queryString, type: REPOSITORY, first: 40, after: $afterCursor) {
                pageInfo { hasNextPage endCursor }
                nodes {
                ... on Repository {
                    name owner { login } hasIssuesEnabled licenseInfo { spdxId }
                    primaryLanguage { name } issues(states: OPEN) { totalCount }
                    pullRequests(states: MERGED) { totalCount } url stargazerCount pushedAt
                  }
                }
              }
            }
            """;

    public GithubRepoInfoScrapper(
            ScrappedRepoInfoRepository scrappedRepoInfoRepository,
            @Value("${app.githubPAT}") String appGithubPat,
            SchedulerManagerService schedulerManagerService,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.scrappedRepoInfoRepository = scrappedRepoInfoRepository;
        this.schedulerManagerService = schedulerManagerService;
        this.restClient = RestClient.builder().requestFactory(factory)
                .baseUrl("https://api.github.com")
                .defaultHeader("User-Agent", "Mr-Aggregator")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("Authorization", "Bearer " + appGithubPat)
                .build();
    }

    // ==========================================
    // 1. ORCHESTRATOR: REPO SCRAPPER (BACKFILL)
    // ==========================================
    @Scheduled(fixedDelay = 14400000)
    public void scrapeRepos() throws Exception {
        ScrapperMetrics metrics = schedulerManagerService.getMetrics("repo-scrapper");

        if (metrics.getStatus().get().equals("PAUSED")) {
            System.out.println("[JOB: scrapeRepos()] [PAUSED]");
            return;
        }

        initializeScrapeMetrics(metrics);
        LocalDate cursorDate = START_DATE;
        LocalDate targetEnd = LocalDate.now();

        while (cursorDate.isBefore(targetEnd)) {
            if (metrics.getStatus().get().equals("PAUSED")) return;

            LocalDate endWindowDate = cursorDate.plusDays(3);
            System.out.println("Scanning window: " + cursorDate + " to " + endWindowDate);

            processDateWindow(cursorDate, endWindowDate, metrics);

            cursorDate = cursorDate.plusDays(3);
            metrics.getProgress().addAndGet(3);
        }

        System.out.println("🏁 Backfill complete! Database populated.");
        metrics.getStatus().set("IDLE");
    }

    private void processDateWindow(LocalDate startDate, LocalDate endDate, ScrapperMetrics metrics) throws InterruptedException {
        String starsFilter = computeStarsFilter(startDate);
        String searchString = String.format("%s created:%s..%s pushed:>2026-03-01 archived:false fork:false", starsFilter, startDate, endDate);

        boolean hasNextPage = true;
        String currentCursor = null;

        // Pagination Loop
        while (hasNextPage) {
            if (metrics.getStatus().get().equals("PAUSED")) return;

            GithubGraphqlResponse response = executeWithRetry(searchString, currentCursor);

            if (response != null && response.data() != null && response.data().search() != null) {
                saveToDb(response.data().search().nodes(), metrics);
                hasNextPage = response.data().search().pageInfo().hasNextPage();
                currentCursor = response.data().search().pageInfo().endCursor();
            } else {
                hasNextPage = false; // Stop paginating if the response is completely broken
            }
            Thread.sleep(4000);
        }
    }

    private GithubGraphqlResponse executeWithRetry(String searchString, String cursor) throws InterruptedException {
        int attempt = 0;
        int maxAttempts = 5;

        Map<String, Object> variables = new HashMap<>();
        variables.put("queryString", searchString);
        if (cursor != null) variables.put("afterCursor", cursor);

        Map<String, Object> requestBody = Map.of("query", SEARCH_GRAPHQL_QUERY, "variables", variables);

        while (attempt < maxAttempts) {
            try {
                return restClient.post()
                        .uri("/graphql")
                        .body(requestBody)
                        .retrieve()
                        .body(GithubGraphqlResponse.class);

            } catch (org.springframework.web.client.HttpServerErrorException e) {
                System.err.println("⚠️ GitHub Server Error (" + e.getStatusCode() + ") on attempt " + attempt + ". Retrying...");
                attempt++;
                Thread.sleep(attempt * 3000L);
            } catch (Exception e) {
                System.err.println("⚠️ Request/Parsing failed on attempt " + attempt + ". Reason: " + e.getMessage());
                attempt++;
                Thread.sleep(attempt * 3000L);
            }
        }
        return null; // Return null if all 5 attempts fail
    }

    // ==========================================
    // 2. ORCHESTRATOR: README FETCHER
    // ==========================================
    @Scheduled(fixedDelay = 300000) // 5 mins
    public void getRepoReadme() throws InterruptedException {
        ScrapperMetrics metrics = schedulerManagerService.getMetrics("repo-readme-scrapper");

        if (metrics.getStatus().get().equals("PAUSED")) {
            System.out.println("[JOB: getRepoReadme()] [PAUSED]");
            return;
        }

        List<ScrappedRepoInfo> repos = scrappedRepoInfoRepository.findTop50ByRawReadmeIsNull();
        if (repos == null || repos.isEmpty()) return;

        initializeReadmeMetrics(metrics, repos.size());
        processReadmeBatch(repos, metrics);
        scrappedRepoInfoRepository.saveAll(repos);
    }

    private void processReadmeBatch(List<ScrappedRepoInfo> repos, ScrapperMetrics metrics) throws InterruptedException {
        int found = 0;
        for (ScrappedRepoInfo repo : repos) {
            if (metrics.getStatus().get().equals("PAUSED")) {
                System.out.println("[JOB: getRepoReadme()] [PAUSED]");
                return; // The caller (getRepoReadme) will save whatever was processed so far
            }

            String readMe = fetchReadme(repo.getRepoName());
            metrics.getPointsCostInCurrentCycle().addAndGet(1);

            if (readMe != null) {
                metrics.getProgress().addAndGet(1);
                repo.setRawReadme(readMe);
                found++;
            }
            Thread.sleep(1000);
        }
        metrics.getTotalPointsCost().addAndGet(repos.size());
        System.out.println("[JOB: getRepoReadme()][Found " + found + "/" + repos.size() + " Readmes]");
    }

    public String fetchReadme(String repoName) {
        String url = "/repos/" + repoName + "/readme";
        try {
            String gitResponseBody = restClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        System.err.println("GitHub API 4xx Error for " + repoName + ": " + response.getStatusCode());
                    })
                    .body(String.class);

            if (gitResponseBody == null) return null;

            JsonNode rootNode = objectMapper.readTree(gitResponseBody);
            String base64Content = rootNode.path("content").asString();

            if (base64Content == null) return null;

            String cleanBase64 = base64Content.replaceAll("\\s+", "");
            return new String(Base64.getDecoder().decode(cleanBase64));

        } catch (Exception e) {
            System.err.println("Error fetching README for " + url + ": " + e.getMessage());
            return null;
        }
    }

    // ==========================================
    // 3. UTILS & DATABASE HANDLERS
    // ==========================================
    private void saveToDb(List<GraphqlRepoNode> nodes, ScrapperMetrics metrics) {
        if (nodes == null || nodes.isEmpty()) return;

        System.out.println("Filtering valid repositories...");
        List<ScrappedRepoInfo> nodesToSave = new ArrayList<>();

        for (GraphqlRepoNode node : nodes) {
            boolean hasIssues = node.hasIssuesEnabled();
            boolean hasValidLicense = node.licenseInfo() != null;
            long openIssuesCount = (node.issues() != null) ? node.issues().totalCount() : 0;
            long mergedPrCount = (node.pullRequests() != null) ? node.pullRequests().totalCount() : 0;

            if (hasIssues && hasValidLicense && openIssuesCount > 5 && mergedPrCount > 100) {
                ScrappedRepoInfo entity = new ScrappedRepoInfo();
                entity.fromRequest(node);
                nodesToSave.add(entity);
            }
        }

        if (!nodesToSave.isEmpty()) {
            scrappedRepoInfoRepository.saveAll(nodesToSave);
            System.out.println("📦 SAVED BATCH: " + nodesToSave.size() + " repositories committed to DB.");
            metrics.getCount().addAndGet(nodesToSave.size());
        } else {
            System.out.println("⚪ Batch processed, but 0 repositories passed the strict quality gates.");
        }
    }

    private String computeStarsFilter(LocalDate date) {
        int year = date.getYear();
        if (year >= 2008 && year < 2019) return "stars:>2000";
        if (year >= 2019 && year < 2023) return "stars:>1000";
        if (year >= 2023 && year < 2026) return "stars:>600";
        return "stars:>100";
    }

    private void initializeScrapeMetrics(ScrapperMetrics metrics) {
        metrics.getStatus().set("RUNNING");
        metrics.getTotal().set(6461); // Expected days/batches
        System.out.println("STARTING Repo Aggregator  {BACKFILL MODE}...");
    }

    private void initializeReadmeMetrics(ScrapperMetrics metrics, int total) {
        metrics.getStatus().set("RUNNING");
        metrics.getTotal().set(total);
        metrics.getPointsCostInCurrentCycle().set(0);
        System.out.println("[JOB: getRepoReadme()][Scanning for Readmes]");
    }
}