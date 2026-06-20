package com.toomanyissues.api.Service.Helper;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LabelMapper {
    public static final Map<String, List<String>> CATEGORY_MAP = Map.of(
            "bug", List.of("bug", "type: bug", "kind/bug"),
            "feature", List.of("enhancement", "feature", "feature request", "type: feature"),
            "documentation", List.of("documentation", "docs", "type: docs"),
            "ui/ux", List.of("ui", "ux", "design", "frontend", "css"),
            "refactor", List.of("refactor", "tech debt", "cleanup", "code quality"),
            "testing", List.of("test", "testing", "qa", "coverage"),
            "performance", List.of("performance", "optimization", "speed"),
            "security", List.of("security", "vulnerability"),
            "accessibility", List.of("accessibility", "a11y"),
            "build", List.of("build", "ci", "cd", "docker", "devops")
    );
    public List<String> expandTags(List<String> frontendTags) {
        if (frontendTags == null || frontendTags.isEmpty()) return List.of();

        List<String> expandedList = new ArrayList<>();
        for (String tag : frontendTags) {
            String normalizedTag = tag.toLowerCase();
            expandedList.addAll(CATEGORY_MAP.getOrDefault(normalizedTag, List.of(normalizedTag)));
        }
        return expandedList;
    }
}
