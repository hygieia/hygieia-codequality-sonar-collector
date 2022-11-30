package com.capitalone.dashboard.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
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

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.CodeQualityType;
import com.capitalone.dashboard.model.SonarProject;

@ExtendWith(MockitoExtension.class)
public class DefaultSonar6ClientTest {
    @Mock
    private RestOperationsSupplier restOperationsSupplier;
    @Mock
    private RestOperations rest;
    @Mock
    private SonarSettings settings;
    @Mock
    private RestClient restClient;
    private DefaultSonar6Client defaultSonar6Client;

    private static final String URL_RESOURCES = "/api/components/search?qualifiers=TRK&ps=500";
    private static final String URL_RESOURCE_DETAILS = "/api/measures/component?format=json&componentId=%s&metricKeys=%s&includealerts=true";
    private static final String URL_PROJECT_ANALYSES = "/api/project_analyses/search?project=%s";
    private static final String SONAR_URL = "http://sonar.com";
    static final String METRICS = "ncloc,violations,new_vulnerabilities,critical_violations,major_violations,blocker_violations,tests,test_success_density,test_errors,test_failures,coverage,line_coverage,sqale_index,alert_status,quality_gate_details";

    @BeforeEach
    public void init() {
        settings = new SonarSettings();
        when(restOperationsSupplier.get()).thenReturn(rest);
        defaultSonar6Client = new DefaultSonar6Client(new RestClient(restOperationsSupplier), settings);
    }

    @Test
    public void getProjects() throws Exception {
        String projectJson = getJson("sonar6projects.json");
        String projectsUrl = SONAR_URL + URL_RESOURCES;
        defaultSonar6Client.setServerCredentials("username", "password", "token");
        doReturn(new ResponseEntity<>(projectJson, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        List<SonarProject> projects = defaultSonar6Client.getProjects(SONAR_URL);
        assertEquals(projects.size(), 2);
        
        assertThat(projects.get(0).getProjectName()).isEqualTo("com.capitalone.test:TestProject");
        assertThat(projects.get(1).getProjectName()).isEqualTo("com.capitalone.test:AnotherTestProject");
        assertThat(projects.get(0).getProjectId()).isEqualTo("AVu3b-MAphY78UZXuYHp");
        assertThat(projects.get(1).getProjectId()).isEqualTo("BVx3b-MAphY78UZXuYHp");
    }

    @Test
    public void getProjects500() throws Exception {
        String projectJson500 = getJson("sonar6projects500.json");
        String projectJson1000 = getJson("sonar6projects1000.json");
        String projectJson1500 = getJson("sonar6projects1500.json");
        String projectJson2000 = getJson("sonar6projects2000.json");
        String projectsUrl = SONAR_URL + URL_RESOURCES;
        String projectsUrl1 = SONAR_URL + URL_RESOURCES+"&p=1";
        String projectsUrl2 = SONAR_URL + URL_RESOURCES+"&p=2";
        String projectsUrl3 = SONAR_URL + URL_RESOURCES+"&p=3";
        String projectsUrl4 = SONAR_URL + URL_RESOURCES+"&p=4";
        
        defaultSonar6Client.setServerCredentials("username", "password", "token");
        
        doReturn(new ResponseEntity<>(projectJson500, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(projectJson500, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl1), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(projectJson1000, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl2), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(projectJson1500, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl3), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(projectJson2000, HttpStatus.OK)).when(rest).exchange(eq(projectsUrl4), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        List<SonarProject> projects = defaultSonar6Client.getProjects(SONAR_URL);
        assertEquals(projects.size(), 2000);
    }

    @Test
    public void currentCodeQuality() throws Exception {
        String measureJson = getJson("sonar6measures.json");
        String analysesJson = getJson("sonar6analyses.json");
        SonarProject project = getProject();
        String measureUrl = String.format(SONAR_URL + URL_RESOURCE_DETAILS,project.getProjectId(),METRICS);
        String analysesUrl = String.format(SONAR_URL + URL_PROJECT_ANALYSES,project.getProjectName());
        
        defaultSonar6Client.setServerCredentials("username", "password", "token");
       
        doReturn(new ResponseEntity<>(measureJson, HttpStatus.OK)).when(rest).exchange(eq(measureUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(analysesJson, HttpStatus.OK)).when(rest).exchange(eq(analysesUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        CodeQuality quality = defaultSonar6Client.currentCodeQuality(getProject());
        assertEquals(quality.getMetrics().size(), 15);
        
        assertThat(quality.getType().name()).isEqualTo(CodeQualityType.StaticAnalysis.name());
        assertThat(quality.getName()).isEqualTo("com.capitalone.test:TestProject");
        assertThat(quality.getVersion()).isEqualTo("2.0.0");
    }


    @Test
    public void currentCodeQualityForNullProjectData() throws Exception {
        String measureJson = getJson("sonar6measures.json");
        String analysesJson = getJson("sonar6analysesNull.json");
        SonarProject project = getProject();
        String measureUrl = String.format(SONAR_URL + URL_RESOURCE_DETAILS,project.getProjectId(),METRICS);
        String analysesUrl = String.format(SONAR_URL + URL_PROJECT_ANALYSES,project.getProjectName());
        
        defaultSonar6Client.setServerCredentials("username", "password", "token");
        doReturn(new ResponseEntity<>(measureJson, HttpStatus.OK)).when(rest).exchange(eq(measureUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        doReturn(new ResponseEntity<>(analysesJson, HttpStatus.OK)).when(rest).exchange(eq(analysesUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        
        //when(restClient.makeRestCallGet(anyString(),any(HttpHeaders.class))).thenReturn(new ResponseEntity<>(measureJson, HttpStatus.OK));
        
        CodeQuality quality = defaultSonar6Client.currentCodeQuality(getProject());
        assertEquals(quality.getMetrics().size(), 15);
        
        assertThat(quality.getType().name()).isEqualTo(CodeQualityType.StaticAnalysis.name());
        assertThat(quality.getName()).isEqualTo("com.capitalone.test:TestProject");
    }



    private String getJson(String fileName) throws IOException {
        InputStream inputStream = DefaultSonar6ClientTest.class.getResourceAsStream(fileName);
        return IOUtils.toString(inputStream);
    }


    private SonarProject getProject() {
        SonarProject project = new SonarProject();
        project.setInstanceUrl(SONAR_URL);
        project.setProjectName("com.capitalone.test:TestProject");
        project.setProjectId("AVu3b-MAphY78UZXuYHp");
        return project;
    }
}
