package com.toomanyissues.api.Service.DTOs.GithubGraphql;

import com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs.GraphqlIssueNode;

import java.util.List;

public record AliasIssueConnection(List<GraphqlIssueNode> nodes) {}
