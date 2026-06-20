package com.toomanyissues.api.Controller.Reponses.AdminResponses;

import java.util.Map;

public record IssueStatusResponse(
        Long total,
        Long fresh,
        Long old,
        Map<String,Long> topPrimaryLanguage,
        Map<String,Long> topRepoName,
        Map<String,Long> topLabelMetrics
) {
}
