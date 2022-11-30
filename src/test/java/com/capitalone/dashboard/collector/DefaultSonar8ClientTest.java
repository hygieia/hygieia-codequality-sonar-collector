package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.CodeQualityType;
import com.capitalone.dashboard.model.SonarProject;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.capitalone.dashboard.collector.DefaultSonar6Client.URL_PROJECT_ANALYSES;
import static com.capitalone.dashboard.collector.DefaultSonar6ClientTest.METRICS;
import static com.capitalone.dashboard.collector.DefaultSonar8Client.URL_RESOURCE_DETAILS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DefaultSonar8ClientTest {
    @Mock
    private RestOperationsSupplier restOperationsSupplier;
    @Mock
    private RestOperations rest;
    @Mock
    private SonarSettings settings;
    private DefaultSonar8Client defaultSonar8Client;


    private static final String SONAR_URL = "http://sonar.com";
    private static final String URL_RESOURCES = "/api/components/search?qualifiers=TRK&ps=500";

    @BeforeEach
    public void init() {
        settings = new SonarSettings();
        when(restOperationsSupplier.get()).thenReturn(rest);
        defaultSonar8Client = new DefaultSonar8Client(new RestClient(restOperationsSupplier), settings);
    }

    @Test
    public void getChangeLog() throws Exception {
        JSONObject qualityProfile = new JSONObject();
        qualityProfile.put("key", "key");
        qualityProfile.put("name", "name");
        qualityProfile.put("language", "java");
        String changeLogJson = getJson("sonar83changelog.json");
        String changelogUrl = String.format(SONAR_URL + DefaultSonar8Client.URL_QUALITY_PROFILE_CHANGES,"name","java");
        defaultSonar8Client.setServerCredentials("username", "password", "token");
        doReturn(new ResponseEntity<>(changeLogJson, HttpStatus.OK)).when(rest).exchange(eq(changelogUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        JSONArray events = defaultSonar8Client.getQualityProfileConfigurationChanges(SONAR_URL,qualityProfile);
        assertEquals(events.size(), 3);
    }

    @Test
    public void getProjects() throws Exception {
        String projectJson = getJson("sonar8projects.json");
        String projectsUrl = SONAR_URL + URL_RESOURCES;
        defaultSonar8Client.setServerCredentials("username", "password", "token");
        doReturn(new ResponseEntity<>(projectJson, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        List<SonarProject> projects = defaultSonar8Client.getProjects(SONAR_URL);
        assertEquals(projects.size(), 2);
        assertEquals(projects.get(0).getProjectName(), "Project Name");
        assertEquals(projects.get(1).getProjectName(), "Project Name 2");
        assertEquals(projects.get(0).getProjectId(), "project-key");
        assertEquals(projects.get(1).getProjectId(), "project-key-2");
    }

    @Test
    public void currentCodeQuality() throws Exception {
        String measureJson = getJson("sonar8measures.json");
        String analysesJson = getJson("sonar8analyses.json");
        SonarProject project = getProject();
        String measureUrl = String.format(SONAR_URL + URL_RESOURCE_DETAILS,project.getProjectId(),METRICS);
        String analysesUrl = String.format(SONAR_URL + URL_PROJECT_ANALYSES,project.getProjectName());
        defaultSonar8Client.setServerCredentials("username", "password", "token");
        doReturn(new ResponseEntity<>(measureJson, HttpStatus.OK)).when(rest).exchange(eq(measureUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(analysesJson, HttpStatus.OK)).when(rest).exchange(eq(analysesUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        CodeQuality quality = defaultSonar8Client.currentCodeQuality(getProject());
        assertEquals(quality.getMetrics().size(), 11);
        assertEquals(quality.getType(), CodeQualityType.StaticAnalysis);
        assertEquals(quality.getName(), "test");
        assertEquals(quality.getVersion(), "1.0");
    }

    private SonarProject getProject() {
        SonarProject project = new SonarProject();
        project.setInstanceUrl(SONAR_URL);
        project.setProjectName("test");
        project.setProjectId("test");
        return project;
    }


    private String getJson(String fileName) throws IOException {
        InputStream inputStream = DefaultSonar8ClientTest.class.getResourceAsStream(fileName);
        return IOUtils.toString(inputStream);
    }
}
