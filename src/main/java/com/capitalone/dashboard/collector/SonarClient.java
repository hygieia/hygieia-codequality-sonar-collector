package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.SonarProject;

import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.web.client.HttpClientErrorException;

public interface SonarClient {

    /** register server credentials before calling getProjects
     * username and password override token when all arguments are not blank
     * @param username for subsequent requests to sonarqube
     * @param password for subsequent requests to sonarqube
     * @param token for subsequent requests to sonarqube
     */
    void setServerCredentials(String username, String password, String token);
    List<SonarProject> getProjects(String instanceUrl);
    CodeQuality currentCodeQuality(SonarProject project) throws HttpClientErrorException, ParseException;
    JSONArray getQualityProfiles(String instanceUrl) throws ParseException;
    List<String> retrieveProfileAndProjectAssociation(String instanceUrl,JSONObject qualityProfile) throws ParseException;
    JSONArray getQualityProfileConfigurationChanges(String instanceUrl,JSONObject qualityProfile) throws ParseException;
    SonarProject getProject(String projectKey, String instanceUrl);
}
