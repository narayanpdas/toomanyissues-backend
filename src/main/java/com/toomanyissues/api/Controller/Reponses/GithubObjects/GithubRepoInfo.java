package com.toomanyissues.api.Controller.Reponses.GithubObjects;

public record GithubRepoInfo(
        String full_name,
        String html_url,
        String description,
        Integer stargazers_count
){
    @Override
    public String toString() {
        return "GithubRepoInfo{" +
                "full_name='" + full_name + '\'' +
                ", html_url='" + html_url + '\'' +
                ", description='" + description + '\'' +
                ", stargazers_count=" + stargazers_count +
                '}';
    }
}
