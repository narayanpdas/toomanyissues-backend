package com.toomanyissues.api.Controller.Reponses.GithubObjects;

public record GithubUserInfo (
    String node_id,
    String login,
    String html_url
){}
