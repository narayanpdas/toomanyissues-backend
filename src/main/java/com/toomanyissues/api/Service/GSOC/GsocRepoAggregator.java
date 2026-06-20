package com.toomanyissues.api.Service.GSOC;

import com.fasterxml.jackson.databind.JsonNode;
import com.toomanyissues.api.Model.ScrappedRepoInfo;
import com.toomanyissues.api.repository.ScrappedRepoInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("gsoc")
public class GsocRepoAggregator implements CommandLineRunner {

    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile(
            "github\\.com/([^/]+)/([^/\\s?]+)"
    );
    private final ScrappedRepoInfoRepository scrappedRepoInfoRepository;
    private final RestClient restClient;

    public GsocRepoAggregator(@Value("${app.githubPAT}") String appGithubPat,
                              ScrappedRepoInfoRepository scrappedRepoInfoRepository


    ) {
        this.scrappedRepoInfoRepository = scrappedRepoInfoRepository;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("-------------GsocRepoAggregator started (DEEP SEARCH MODE)-------------");
        try {
            ObjectMapper mapper = new ObjectMapper();
            String rawJson = restClient.get()
                    .uri("https://api.gsocorganizations.dev/organizations.json")
                    .retrieve()
                    .body(String.class);
            JsonNode rootNode = mapper.readTree(rawJson);

            int updatedCount = 0;

            if (rootNode.isArray()) {
                for (JsonNode orgNode : rootNode) {
                    if (!orgNode.has("years")) continue;

                    JsonNode yearsNode = orgNode.get("years");
                    Iterator<String> years = yearsNode.fieldNames();


                    while (years.hasNext()) {
                        String year = years.next();
                        JsonNode yearNode = yearsNode.get(year);
                        if (!yearNode.has("projects")) continue;
                        JsonNode projectsNode = yearNode.get("projects");
                        for (JsonNode projectNode : projectsNode) {
                            if (!projectNode.has("code_url")) continue;
                            String codeUrl = projectNode.get("code_url").asText();
                            String exactRepoName = extractGitHubRepo(codeUrl);

                            if (exactRepoName != null) {
                                // 4. Search your 8,300 database entries for an EXACT match
                                List<ScrappedRepoInfo> matches = scrappedRepoInfoRepository
                                        .findByRepoNameIgnoreCase(exactRepoName);

                                for (ScrappedRepoInfo repo : matches) {
                                    if (!"GSOC".equals(repo.getRepoType())) {
                                        repo.setRepoType("GSOC");
                                        scrappedRepoInfoRepository.save(repo);
                                        updatedCount++;
                                        System.out.println("🎯 Match Found & Tagged: " + exactRepoName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("✅ Success! Deep-parsed and tagged " + updatedCount + " precise GSoC repos.");

        } catch (Exception e) {
            System.err.println("❌ Parser Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractGitHubRepo(String url) {
        if (url == null || !url.contains("github.com") || url.contains("gist.github.com")) {
            return null;
        }

        Matcher matcher = GITHUB_REPO_PATTERN.matcher(url);
        if (matcher.find()) {
            String owner = matcher.group(1);
            String repo = matcher.group(2);


            if (repo.endsWith(".git")) {
                repo = repo.substring(0, repo.length() - 4);
            }
            return owner + "/" + repo;
        }
        return null;
    }
}
