package com.toomanyissues.api.Service;

import com.toomanyissues.api.Model.ScrapperMetrics;
import org.springframework.stereotype.Service;


import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchedulerManagerService {
    private final Map<String, ScrapperMetrics> scrapers = new ConcurrentHashMap<>();

    public SchedulerManagerService() {
        String defaultStatus = "PAUSED";
        scrapers.put("repo-scrapper",new ScrapperMetrics(
                "repo-scrapper",
                "GitHub Repository Scraper",
                "Fetches top repositories by from 2008.",
                "PAUSED"
        ));
        scrapers.put("issue-scraper-indie", new ScrapperMetrics(
                "issue-scraper-indie",
                "GitHub Indie Issue Scraper",
                "Finds Issues of Indie repos.",
                defaultStatus
        ));
        scrapers.put("issue-scraper-enterprise", new ScrapperMetrics(
                "issue-scraper-enterprise",
                "GitHub Enterprise Issue Scraper",
                "Finds issues of Enterprise repos.",
                defaultStatus
        ));
        scrapers.put("repo-readme-scrapper", new ScrapperMetrics(
                "repo-readme-scrapper",
                "Readme Scraper",
                "Scraps unavailable Readme of repos.",
                defaultStatus
        ));
        scrapers.put("repo-readme-summarizer", new ScrapperMetrics(
                "repo-readme-summarizer",
                "Gemini Readme Summarizer",
                "Summarizes readme of repos.",
                defaultStatus
        ));
    }
    public ScrapperMetrics getMetrics(String scraperId) {
        return scrapers.get(scraperId);
    }
    public Collection<ScrapperMetrics> getAllMetrics() {
        return scrapers.values();
    }

}
