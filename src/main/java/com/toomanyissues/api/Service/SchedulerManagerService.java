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
        scrapers.put("issue-scraper-hot", new ScrapperMetrics(
                "issue-scraper-hot",
                "GitHub hot Issue Scraper",
                "Finds Issues of hot repos.",
                defaultStatus
        ));
        scrapers.put("issue-scraper-warm", new ScrapperMetrics(
                "issue-scraper-warm",
                "GitHub warm Issue Scraper",
                "Finds issues of warm repos.",
                defaultStatus
        ));
        scrapers.put("issue-scraper-cold", new ScrapperMetrics(
                "issue-scraper-cold",
                "GitHub cold Issue Scraper",
                "Finds issues of cold repos.",
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
