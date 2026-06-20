package com.toomanyissues.api.Controller.Reponses.GithubObjects;

import java.util.List;

public record GithubSearchResponse(List<GithubIssueInfo> items) {
    public Integer getSize() {
        return items.isEmpty()?0:items.size();
    }

    @Override
    public String toString() {
        return "GithubSearchResponse{" +
                "items=" + items +
                '}';
    }
}
