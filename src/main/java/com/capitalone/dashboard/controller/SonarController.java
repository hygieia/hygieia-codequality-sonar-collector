package com.capitalone.dashboard.controller;

import com.capitalone.dashboard.collector.SonarClient;
import com.capitalone.dashboard.collector.SonarClientSelector;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.SonarProject;
import com.capitalone.dashboard.repository.CodeQualityRepository;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.repository.SonarProjectRepository;
import com.capitalone.dashboard.util.SonarCollectorUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Validated
public class SonarController {

    private final SonarProjectRepository sonarProjectRepository;
    private final CodeQualityRepository codeQualityRepository;
    private final CollectorRepository collectorRepository;
    private SonarClientSelector sonarClientSelector;


    private String instanceUrl;
    private String projectName;
    private String projectKey;
    private Collector collector;
    private SonarClient sonarClient;

    @Autowired
    public SonarController(SonarProjectRepository sonarProjectRepository,
                           CodeQualityRepository codeQualityRepository,
                           CollectorRepository collectorRepository,
                           SonarClientSelector sonarClientSelector) {
        this.sonarProjectRepository = sonarProjectRepository;
        this.codeQualityRepository = codeQualityRepository;
        this.collectorRepository = collectorRepository;
        this.sonarClientSelector = sonarClientSelector;
    }

    @RequestMapping(value = "/refresh", method = GET, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> refresh(@Valid String projectName, @Valid String projectKey, @Valid String instanceUrl) {

        gatherParams(projectName, projectKey, instanceUrl);
        if (Objects.nonNull(instanceUrl)) {
            this.sonarClient = this.sonarClientSelector.getSonarClient(this.sonarClientSelector.getSonarVersion(instanceUrl));
            this.collector = collectorRepository.findByName("Sonar");

            SonarProject projectToRefresh;
            if (Objects.nonNull(this.collector)) {
                if ((Objects.nonNull(projectName) && Objects.nonNull(projectToRefresh = getExistingProject()))
                || (Objects.nonNull(projectKey) && Objects.nonNull(projectToRefresh = createNewProjectIfNotExists()))) {
                    SonarCollectorUtil.updateCodeQualityData(this.collector, this.sonarClient, projectToRefresh);
                    return sendResponse("successfully refreshed sonar project");
                }
                return sendResponse("unable to refresh sonar project");
            }
            return sendResponse("sonar collector not found");
        }
        return sendResponse("sonar instance url is invalid");
    }

    private void gatherParams(String projectName, String projectKey, String instanceUrl) {
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.instanceUrl = instanceUrl;
    }

    private ResponseEntity<String> sendResponse(String message) {
//        StringBuilder status = new StringBuilder(message);
//        status.append(String.format(" - projectName=%s projectKey=%s instanceUrl=%s ", Objects.toString(this.projectName, ""),
//                Objects.toString(this.projectKey, ""), this.instanceUrl));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(message, httpHeaders, HttpStatus.OK);
    }

    private SonarProject createNewProjectIfNotExists() {
        SonarProject project = null;
        if (Objects.nonNull(this.projectKey)) {
            SonarProject newProject = this.sonarClient.getProject(this.projectKey, this.instanceUrl);
            if (Objects.nonNull(newProject)) {
                SonarProject existingProject = sonarProjectRepository.findSonarProject(this.collector.getId(),
                        newProject.getInstanceUrl(), newProject.getProjectId());
                project = Objects.nonNull(existingProject) ? existingProject : newProject;
            }
        }
        return project;
    }

    private SonarProject getExistingProject() {
        List<SonarProject> sonarProjects = sonarProjectRepository.
                findSonarProjectsByProjectName(this.collector.getId(), this.instanceUrl, this.projectName);
        return CollectionUtils.isNotEmpty(sonarProjects) ?
                sonarProjects.stream().sorted(Comparator.comparing(SonarProject::getLastUpdated).reversed()).findFirst().get() : null;
    }
}
