package com.toomanyissues.api.Service.DTOs.GithubGraphql;


import java.util.List;
import java.util.Map;

public record GraphqlAliasResponse(Map<String, AliasRepository> data, List<GraphqlError> errors) {}

