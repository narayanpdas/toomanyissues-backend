package com.toomanyissues.api.Service.DTOs.AiSummarizationDTOs;

public record SummarizationMetaData(
        String rawReadme,
        String aiSummary,
        String aiSummaryStatus
){}
