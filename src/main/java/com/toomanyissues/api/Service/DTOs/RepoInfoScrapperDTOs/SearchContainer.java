package com.toomanyissues.api.Service.DTOs.RepoInfoScrapperDTOs;

import java.util.List;

public record SearchContainer(PageInfo pageInfo,List<GraphqlRepoNode> nodes) {}
