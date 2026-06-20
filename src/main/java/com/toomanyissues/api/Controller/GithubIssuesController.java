package com.toomanyissues.api.Controller;

import com.toomanyissues.api.Controller.Reponses.IssueSummaryResponse;
import com.toomanyissues.api.Model.GithubIssues;
import com.toomanyissues.api.Model.User;
import com.toomanyissues.api.Service.AISummarizationService;
import com.toomanyissues.api.Service.GithubIssuesService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import java.util.List;



@RestController
public class GithubIssuesController {
    GithubIssuesService githubIssuesService;
    AISummarizationService  aisSummarizationService;
    public GithubIssuesController(GithubIssuesService githubIssuesService, AISummarizationService aisSummarizationService) {
        this.githubIssuesService = githubIssuesService;
        this.aisSummarizationService = aisSummarizationService;
    }
    @GetMapping("api/issues/recommended")
    public ResponseEntity<Page<GithubIssues>> getRecommendedIssues(@AuthenticationPrincipal User currentUser,
                                                                   @RequestParam(required = false) List<String> primaryLanguages,
                                                                   @RequestParam(required = false) List<String> labels,
                                                                   @RequestParam(name="search",required = false) String keyword,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size
                                                                   ){
        int MAXSIZE = 50;
        int minSize = Math.min(size, MAXSIZE);
        Page<GithubIssues> results = githubIssuesService.getRecommendedIssues(currentUser,primaryLanguages,labels,keyword,page,minSize);
        return ResponseEntity.ok(results);
    }
    @GetMapping("/api/issues/recent")
    public ResponseEntity<Page<GithubIssues>> getIssues(
            @RequestParam(required = false) List<String> primaryLanguages,
            @RequestParam(required = false) List<String> labels,
            @RequestParam(name="search",required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ){
        int MAXSIZE = 50;
        int minSize = Math.min(size, MAXSIZE);
        Page<GithubIssues> results = githubIssuesService.getIssues(primaryLanguages,labels,keyword,page,minSize);
        return ResponseEntity.ok(results);
    }
    @GetMapping("/api/issues/summarize/{node_id}")
    public ResponseEntity<?> summarizeIssues(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String node_id
    ){
        String summary = aisSummarizationService.summarizeIssue(currentUser.getUsername(),node_id);
        if(summary == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.ok(new IssueSummaryResponse(node_id,summary));
    }
    @GetMapping("/api/issues/shared/{node_id}")
    public ResponseEntity<?> getIssuesById(@PathVariable String node_id){
        GithubIssues p = githubIssuesService.getIssuesById(node_id);
        if(p==null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{Error: The id is no available}");
        }
        return ResponseEntity.status(HttpStatus.OK).body(p);
    }
}
