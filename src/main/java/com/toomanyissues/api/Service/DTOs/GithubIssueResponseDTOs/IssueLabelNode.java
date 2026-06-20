package com.toomanyissues.api.Service.DTOs.GithubIssueResponseDTOs;

public record IssueLabelNode(String name, String color) {
    public String toString() {
        return "IssueLabelNode{" +
                "name='" + name + '\'' +
                ", color='" + color + '\'' +
                '}';
    }
}
