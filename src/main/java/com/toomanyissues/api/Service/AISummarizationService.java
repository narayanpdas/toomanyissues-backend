package com.toomanyissues.api.Service;

import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.Model.ScrapperMetrics;
import com.toomanyissues.api.Service.BACKFILL.GithubRepoInfoScrapper;
import com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs.RepoReadmeDTO;
import com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs.SummarizationMetaData;
import com.toomanyissues.api.repository.GithubIssuesRepository;

import com.toomanyissues.api.repository.ScrappedRepoInfoRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AISummarizationService {

    GithubIssuesService githubIssuesService;
    private final StringRedisTemplate redisTemplate;
    GithubIssuesRepository githubIssuesRepository;
    UserService userService;
    GeminiClientService geminiClientService;
    private final ScrappedRepoInfoRepository scrappedRepoInfoRepository;
    GithubRepoInfoScrapper  githubRepoInfoScrapper;
    SchedulerManagerService schedulerManagerService;
    public AISummarizationService(
            GithubIssuesService githubIssuesService,
            StringRedisTemplate redisTemplate,
            GithubIssuesRepository githubIssuesRepository,
            UserService userService,
            GeminiClientService geminiClientService,
            ScrappedRepoInfoRepository scrappedRepoInfoRepository,
            GithubRepoInfoScrapper githubRepoInfoScrapper,
            SchedulerManagerService schedulerManagerService

    ) {

        this.githubIssuesService = githubIssuesService;
        this.redisTemplate = redisTemplate;
        this.githubIssuesRepository = githubIssuesRepository;
        this.userService = userService;
        this.geminiClientService = geminiClientService;
        this.scrappedRepoInfoRepository = scrappedRepoInfoRepository;
        this.githubRepoInfoScrapper = githubRepoInfoScrapper;
        this.schedulerManagerService = schedulerManagerService;
    }
    public String summarizeIssue(String userName,String issueId) {
        GithubIssues issue = githubIssuesService.getIssuesById(issueId);
        if (issue == null) {
            return null;
        }
        if (issue.getSummary() != null && !issue.getSummary().isBlank()) {
            return issue.getSummary();
        }
        SummarizationMetaData issueRepo = scrappedRepoInfoRepository.findByRepoName(issue.getRepoName());
        if (issueRepo == null) {
            return null;
        }
        String lockKey = "lock:generate:summary:" + issueId;
        Boolean acquiredLock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(60));
        if (Boolean.TRUE.equals(acquiredLock)) {
            try {
                boolean quotaDeducted = userService.deductUserPoints(userName);
                if (!quotaDeducted) {
                    throw new RuntimeException("Daily AI Quota Reached. Please try again tomorrow.");
                }
                String rawReadme = issueRepo.rawReadme();
                String repoSummary = issueRepo.aiSummary();
                String repoStatus = issueRepo.aiSummaryStatus();
                boolean dbNeedsUpdate = false;
                if (rawReadme == null || rawReadme.isBlank()) {
                    rawReadme = githubRepoInfoScrapper.fetchReadme(issue.getRepoName());
                    dbNeedsUpdate = true;
                }
                if ("PENDING".equals(repoStatus)) {
                    repoSummary = geminiClientService.summarizeRepository(issue.getRepoName(), rawReadme);
                    repoStatus = "COMPLETED";
                    dbNeedsUpdate = true;
                }
                if (dbNeedsUpdate) {
                    scrappedRepoInfoRepository.updateSummaryInfo(
                            issue.getRepoName(),
                            rawReadme,
                            repoSummary,
                            repoStatus
                    );
                }
                String aiSummary = geminiClientService.summarizeIssue(
                        issue.getTitle(),
                        issue.getBody(),
                        issue.getRepoName(),
                        repoSummary
                );

                issue.setSummary(aiSummary);
                githubIssuesRepository.save(issue);
                return aiSummary;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            int retries = 0;
            int maxRetries = 12;
            while (retries < maxRetries) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Polling interrupted");
                }
                GithubIssues refreshedIssue = githubIssuesRepository.findById(issueId).orElse(null);
                if (refreshedIssue != null && refreshedIssue.getSummary() != null && !refreshedIssue.getSummary().isBlank()) {
                    return refreshedIssue.getSummary();
                }
                retries++;
            }
            throw new RuntimeException("AI generation is taking too long or failed. Please refresh and try again.");
        }
    }

    @Scheduled(fixedDelay = 2700000) //DEV -1800000/30 mins , PROD - 2700000/45mins
    public void getRepoReadmeSummary(){
        ScrapperMetrics metrics = schedulerManagerService.getMetrics("repo-readme-summarizer");

        if ("PAUSED".equals(metrics.getStatus().get())) {
            System.out.println("[JOB: getRepoReadmeSummary()] [PAUSED]");
            return;
        }
        List<RepoReadmeDTO> pendingRepos = fetchPrioritizedRepos();
        if (pendingRepos.isEmpty()) {
            System.out.println("[JOB: getRepoReadmeSummary()] [NOTHING TO PROCESS] [AUTOMATED STATUS UPDATE : PAUSED]");
            metrics.getStatus().set("PAUSED");
            return;
        }

        System.out.println("[JOB: getRepoReadmeSummary()] [Generating AI Summary for " + pendingRepos.size() + " prioritized repos]");
        initializeMetrics(metrics, pendingRepos.size());
        int summaryCreated = 0;
        try {
            for (RepoReadmeDTO repo : pendingRepos) {
                if ("PAUSED".equals(metrics.getStatus().get())) {
                    System.out.println("[JOB: getRepoReadmeSummary()] [PAUSED MID-EXECUTION]");
                    return;
                }
                boolean success = processSingleRepo(repo);
                if (success) {
                    recordSuccessMetrics(metrics);
                    summaryCreated++;
                }
                Thread.sleep(5000);
            }
            System.out.println("[JOB: getRepoReadmeSummary()] [" + summaryCreated + " Summaries created / " + pendingRepos.size() + " Repos processed]");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            System.err.println("[JOB: getRepoReadmeSummary()] Cron job sleep interrupted.");
        } finally {
            if (!"PAUSED".equals(metrics.getStatus().get())) {
                metrics.getStatus().set("IDLE");
            }
        }
    }
    private List<RepoReadmeDTO> fetchPrioritizedRepos() {

        // 1. Try to fill the batch with HOT repos
        List<RepoReadmeDTO> prioritizedBatch = new ArrayList<>(scrappedRepoInfoRepository
                .findTop10ByRawReadmeIsNotNullAndAiSummaryStatusAndActivityTemperature("PENDING", "HOT"));

        // 2. If we need more to reach 10, pull WARM repos
        if (prioritizedBatch.size() < 10) {
            List<RepoReadmeDTO> warmRepos = scrappedRepoInfoRepository
                    .findTop10ByRawReadmeIsNotNullAndAiSummaryStatusAndActivityTemperature("PENDING", "WARM");

            int slotsRemaining = 10 - prioritizedBatch.size();
            prioritizedBatch.addAll(warmRepos.stream().limit(slotsRemaining).toList());
        }

        // 3. If we STILL need more, pull COLD repos
        if (prioritizedBatch.size() < 10) {
            List<RepoReadmeDTO> coldRepos = scrappedRepoInfoRepository
                    .findTop10ByRawReadmeIsNotNullAndAiSummaryStatusAndActivityTemperature("PENDING", "COLD");

            int slotsRemaining = 10 - prioritizedBatch.size();
            prioritizedBatch.addAll(coldRepos.stream().limit(slotsRemaining).toList());
        }

        return prioritizedBatch;
    }

    public static String sanitizeReadmeForLlm(String rawReadme) {
        if (rawReadme == null || rawReadme.isBlank()) {
            return "";
        }
        if (rawReadme.length() > 14000) {
            return rawReadme.substring(0, 14000);
        }
        return rawReadme;
    }
    private boolean processSingleRepo(RepoReadmeDTO repo) {
        try {
            String safeReadme = sanitizeReadmeForLlm(repo.rawReadme());
            String aiSummary = geminiClientService.summarizeRepository(repo.repoName(), safeReadme);

            if (aiSummary != null && !aiSummary.isBlank()) {
                scrappedRepoInfoRepository.updateRepoSummaryStatus(repo.repoName(), aiSummary, "COMPLETED");
                return true;
            } else {
                scrappedRepoInfoRepository.updateRepoSummaryStatus(repo.repoName(), null, "FAILED");
                return false;
            }
        } catch (Exception e) {
            System.err.println("[JOB: getRepoReadmeSummary()] [Failed to summarize repo: " + repo.repoName() + " - " + e.getMessage() + "]");
            scrappedRepoInfoRepository.updateRepoSummaryStatus(repo.repoName(), null, "FAILED");
            return false;
        }
    }
    private void initializeMetrics(ScrapperMetrics metrics, int totalJobs) {
        metrics.getStatus().set("RUNNING");
        metrics.getTotal().set(totalJobs);
        metrics.getProgress().set(0);
        metrics.getPointsCostInCurrentCycle().set(0);
    }
    private void recordSuccessMetrics(ScrapperMetrics metrics) {
        metrics.getProgress().incrementAndGet();
        metrics.getPointsCostInCurrentCycle().incrementAndGet();
        metrics.getTotalPointsCost().incrementAndGet();
    }

}
