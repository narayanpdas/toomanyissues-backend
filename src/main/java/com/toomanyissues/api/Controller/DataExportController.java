package com.toomanyissues.api.Controller;

import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.repository.GithubIssuesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/internal")
public class DataExportController {
    String internalExportKey;
    GithubIssuesRepository githubIssuesRepository;
    public DataExportController(GithubIssuesRepository githubIssuesRepository,
                                @Value("${INTERNAL_EXPORT_KEY}")
                                String internalExportKey) {
        this.githubIssuesRepository = githubIssuesRepository;
        this.internalExportKey = internalExportKey;
    }
    @GetMapping("/export-issues")
    public ResponseEntity<List<GithubIssues>> exportIssues(
            @RequestHeader("Local-Internal-Key") String incomingApiKey,
            @RequestParam("since") String sinceIsoString) {
        System.out.println("SINCE: " + sinceIsoString+"\nApiKey used: "+incomingApiKey);
        if (internalExportKey == null || !internalExportKey.equals(incomingApiKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Instant sinceTime = Instant.parse(sinceIsoString);
        List<GithubIssues> newIssues = githubIssuesRepository.findByScrapedAtAfter(sinceTime);
        return ResponseEntity.ok(newIssues);
    }

}
