package com.toomanyissues.api.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiClientService {
    private final Client client;
    private static final String MODEL_NAME = "gemini-3.1-flash-lite";
    private final Pattern CODE_BLOCK_PATTER = Pattern
            .compile("```(?:[a-zA-Z0-9]+)?\\s*?(.*?)```",Pattern.DOTALL);

    public GeminiClientService() {
        this.client = new Client();
    }
    public String summarizeIssue(String issueName, String issueDescription,
                                 String repositoryName, String repositoryDescription) {
        String newIssueDescription = "";
        if(issueDescription.length() > 8000){
            List<List<String>> blocks = sanitizeIssueDescription(issueDescription);
            List<String> codeBlocks = blocks.get(0); // TODO Not Using for now may be useful in the future
            List<String> NonCodeBlocks = blocks.get(1);
            newIssueDescription = String.join("\n", NonCodeBlocks);
        }
        String PROMPT = """
                SYSTEM INSTRUCTIONS:
                - You are a github expert, based on the information about the a github repository,
                Summarize this GitHub issue into 3 short, bulleted points, focusing on the core problem,
                - If the issue contains large stack traces, CLI logs, or code dumps, ignore the specific code syntax.
                Do not attempt to solve the bug.
                Focus only on summarizing the human-described problem and the high-level system failure.
                GITHUB REPOSITORY NAME:
                {%s}
                GITHUB REPOSITORY DESCRIPTION:
                {%s}
                ISSUE NAME:
                {%s}
                ISSUE DESCRIPTION:
                {%s}
                summary:
                """;
        String request = "";
        if(!newIssueDescription.isEmpty()){
            request = PROMPT.formatted(repositoryName, repositoryDescription, issueName, newIssueDescription);
        }
        else{
            request = PROMPT.formatted(repositoryName, repositoryDescription, issueName, issueDescription);
        }
        try {
            GenerateContentResponse response =
                    client.models.generateContent(
                            MODEL_NAME,
                            request,
                            null
                    );
            return response.text();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to summarizeIssue with error: \n" + e.getMessage()) ;
        }
    }

    public String summarizeRepository(String repositoryName, String repositoryDescription) {
        String newRepositoryDescription = "";

        if(repositoryDescription.length() > 12000){
            newRepositoryDescription = repositoryDescription.substring(0,12000);
        }
        String requestPrompt = getRequestPrompt(repositoryName,
                repositoryDescription,
                newRepositoryDescription);
        try {
            GenerateContentResponse response =
                    client.models.generateContent(
                            MODEL_NAME,
                            requestPrompt,
                            null
                    );
            return response.text();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to summarizeIssue with error: \n" + e.getMessage()) ;
        }
    }

    private static @NonNull String getRequestPrompt(String repositoryName, String repositoryDescription, String newRepositoryDescription) {
        String PROMPT = """
            SYSTEM INSTRUCTIONS:
                You are a expert Software Engineer, based on the description of a github repository,
                generate a 3 line summary focusing on its core aspect.
                REPOSITORY NAME:
                %s
                REPOSITORY DESCRIPTION:
                %s
                summary:
        """;
        String request = "";
        if(!newRepositoryDescription.isEmpty()){
            request = PROMPT.formatted(repositoryName, newRepositoryDescription);
        }
        else request = PROMPT.formatted(repositoryName, repositoryDescription);
        return request;
    }

    public List<List<String>> sanitizeIssueDescription(String issueDescription) {
        List<String> codeBlocks = new ArrayList<>();
        List<String> nonCodeBlocks = new ArrayList<>();

        Matcher matcher = CODE_BLOCK_PATTER.matcher(issueDescription);
        int lastIndex = 0;
        while (matcher.find()) {
            codeBlocks.add(matcher.group(1));
            if(matcher.start() > lastIndex) {
                nonCodeBlocks.add(issueDescription.substring(lastIndex, matcher.start()));
            }
            lastIndex = matcher.end();
        }
        if(lastIndex != issueDescription.length()){
            nonCodeBlocks.add(issueDescription.substring(lastIndex));
        }
        return new ArrayList<>(List.of(codeBlocks,nonCodeBlocks));
    }
}
