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
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Validated
public class SonarController {

    private final SonarProjectRepository sonarProjectRepository;
    private final CodeQualityRepository codeQualityRepository;
    private final CollectorRepository collectorRepository;
    private SonarClientSelector sonarClientSelector;

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

        if (Objects.nonNull(instanceUrl)) {
            SonarClient sonarClient = this.sonarClientSelector.getSonarClient(this.sonarClientSelector.getSonarVersion(instanceUrl));
            Collector collector = collectorRepository.findByName("Sonar");

            SonarProject projectToRefresh;
            if (Objects.nonNull(collector)) {
                if ((Objects.nonNull(projectName) &&
                        Objects.nonNull(projectToRefresh = getExistingProject(collector.getId(),
                                instanceUrl, projectName, projectKey, sonarClient)))) {
                    SonarCollectorUtil.updateCodeQualityData(collector, sonarClient, projectToRefresh);
                    return sendResponse("successfully refreshed sonar project");
                }
                return sendResponse("unable to refresh sonar project");
            }
            return sendResponse("sonar collector not found");
        }
        return sendResponse("sonar instance url is invalid");
    }

    private ResponseEntity<String> sendResponse(String message) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(message, httpHeaders, HttpStatus.OK);
    }

    private SonarProject getExistingProject(ObjectId collectorId, String instanceUrl, String projectName,
                                            String projectKey, SonarClient sonarClient) {
        if (Objects.nonNull(projectKey)) {
            SonarProject project = sonarClient.getProject(projectKey, instanceUrl);
            if (Objects.nonNull(project)) {
                List<SonarProject> sonarProjects = sonarProjectRepository.
                        findSonarProjectsByProjectName(collectorId, instanceUrl, projectName);
                if (CollectionUtils.isNotEmpty(sonarProjects)) {
                    sonarProjects = sonarProjects.stream().filter(p -> p.getProjectId()==null || project.getProjectId().equals(p.getProjectId())).collect(Collectors.toList());
                }
                if (CollectionUtils.isNotEmpty(sonarProjects)) {
                    Collections.sort(sonarProjects, Comparator.comparing(SonarProject::getLastUpdated).reversed());
                    SonarProject existingProject = sonarProjects.get(0);
                    existingProject.setProjectId(project.getProjectId());
                    return existingProject;
                }
            }
        }
        return null;
    }
}
