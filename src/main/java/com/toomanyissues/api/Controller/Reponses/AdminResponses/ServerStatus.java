package com.toomanyissues.api.Controller.Reponses.AdminResponses;

public record ServerStatus(String ramUsage,
                           String dbStorage,
                           String uptime) {
}
