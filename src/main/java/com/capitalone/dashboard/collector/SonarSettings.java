package com.capitalone.dashboard.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bean to hold settings specific to the Sonar collector.
 */
@Component
@ConfigurationProperties(prefix = "sonar")
public class SonarSettings {
    private String cron;
    private String username;
    private String password;
    private List<String> servers;
    private List<String> niceNames;
    private List<String> tokens;
    private String metrics63andAbove; // 6.3 is the sonar version
    private String metricsBefore63;

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public List<String> getNiceNames() {
        return niceNames;
    }

    public void setNiceNames(List<String> niceNames) {
        this.niceNames = niceNames;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }

    public String getMetrics63andAbove() {
        return metrics63andAbove;
    }

    public void setMetrics63andAbove(String metrics63andAbove) {
        this.metrics63andAbove = metrics63andAbove;
    }

    public String getMetricsBefore63() {
        return metricsBefore63;
    }

    public void setMetricsBefore63(String metricsBefore63) {
        this.metricsBefore63 = metricsBefore63;
    }
}
