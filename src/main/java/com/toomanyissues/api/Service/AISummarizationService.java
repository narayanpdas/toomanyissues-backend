package com.toomanyissues.api.Service;

import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.Model.ScrapperMetrics;
import com.toomanyissues.api.Service.BACKFILL.GithubRepoInfoScrapper;
import com.toomanyissues.api.repository.GithubIssuesRepository;

import com.toomanyissues.api.repository.ScrappedRepoInfoRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
        ScrappedRepoInfo issueRepo = scrappedRepoInfoRepository.findByRepoName(issue.getRepoName());
        if(issueRepo == null){
            return null;
        }
        String lockKey = "lock:generate:summary:" + issueId;
        Boolean acquiredLock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(20));

        if (Boolean.TRUE.equals(acquiredLock)) {
            try {
                boolean quotaDeducted = userService.deductUserPoints(userName);
                if (!quotaDeducted) {
                    throw new RuntimeException("Daily AI Quota Reached. Please try again tomorrow.");
                }
                String rawReadme = issueRepo.getRawReadme();
                if(rawReadme == null){
                    rawReadme = githubRepoInfoScrapper.fetchReadme(issueRepo.getRepoName());
                    issueRepo.setRawReadme(rawReadme);
                }
                if(issueRepo.getAiSummary()==null){
                    String repoSummary = geminiClientService
                            .summarizeRepository(issueRepo.getRepoName(), rawReadme);
                    issueRepo.setAiSummary(repoSummary);
                    issueRepo.setAiSummaryStatus("COMPLETED");
                    scrappedRepoInfoRepository.save(issueRepo);
                }
                String aiSummary = geminiClientService.summarizeIssue(
                        issue.getTitle(),
                        issue.getBody(),
                        issue.getRepoName(),
                        issueRepo.getAiSummary()
                );
                issue.setSummary(aiSummary);
                githubIssuesRepository.save(issue);
                return aiSummary;

            } finally {
                redisTemplate.delete(lockKey);
            }
        }
        else {
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

    @Scheduled(fixedDelay = 1800000) // 30 mins , PROD - 1800000, 30mins
    public void getRepoReadmeSummary(){
        ScrapperMetrics metrics =  schedulerManagerService.getMetrics("repo-readme-summarizer");
        if(metrics.getStatus().get().equals("PAUSED")){
            System.out.println("[JOB: getRepoReadmeSummary()] [PAUSED]");
            return;
        }
        List<ScrappedRepoInfo> pendingRepos = scrappedRepoInfoRepository
                .findTop10ByRawReadmeIsNotNullAndAiSummaryStatusAndRepoType("PENDING",
                        "ENTERPRISE");
        metrics.getStatus().set("RUNNING");
        metrics.getTotal().set(10);
        metrics.getPointsCostInCurrentCycle().set(0);
        if (pendingRepos.isEmpty()) {
            return;
        }
        System.out.println("[JOB: getRepoReadmeSummary()] [Generating AI Summary for "+pendingRepos.size()+" ENTERPRISE repos]");
        int summaryCreated = 0;
        for (ScrappedRepoInfo repo : pendingRepos) {
            if(metrics.getStatus().get().equals("PAUSED")){
                System.out.println("[JOB: getRepoReadmeSummary()] [PAUSED]");
                scrappedRepoInfoRepository.saveAll(pendingRepos);
                return;
            }
            try {
                String safeReadme = sanitizeReadmeForLlm(repo.getRawReadme());
                String aiSummary = geminiClientService
                        .summarizeRepository(repo.getRepoName(), safeReadme);
                if(aiSummary!=null && !aiSummary.isBlank()){
                    repo.setAiSummary(aiSummary);
                    repo.setAiSummaryStatus("COMPLETE");
                    metrics.getProgress().addAndGet(1);
                    metrics.getPointsCostInCurrentCycle().addAndGet(1);
                    summaryCreated++;
                }
                Thread.sleep(5000);
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("Cron job sleep interrupted.");
                break;
            }
            catch (Exception e) {
                repo.setAiSummaryStatus("FAILED");
                System.err.println("[JOB: getRepoReadmeSummary()] [Failed to summarize Enterprise repo: " + repo.getRepoName() + " - " + e.getMessage()+"]");
                return;
            }
        }
        metrics.getTotalPointsCost().addAndGet(summaryCreated);
        System.out.println("[JOB: getRepoReadmeSummary()] ["+ summaryCreated+" Summary created /"+pendingRepos.size()+" ENTERPRISE repos]");
        scrappedRepoInfoRepository.saveAll(pendingRepos);

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

}
