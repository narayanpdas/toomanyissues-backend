package com.toomanyissues.api.Controller.Reponses.GithubObjects;

public record GithubLabelInfo(
        String name,
        String color,
        String description
) {
}
