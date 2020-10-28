package com.capitalone.dashboard.controller;

import com.capitalone.dashboard.collector.SonarClient;
import com.capitalone.dashboard.collector.SonarClientSelector;
import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.SonarProject;
import com.capitalone.dashboard.repository.CodeQualityRepository;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.repository.SonarProjectRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import javax.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Validated
public class SonarController {

    private static final Log LOG = LogFactory.getLog(SonarController.class);

    private final SonarProjectRepository sonarProjectRepository;
    private final CodeQualityRepository codeQualityRepository;
    private final CollectorRepository collectorRepository;
    private SonarClientSelector sonarClientSelector;



    @Autowired
    public SonarController(SonarProjectRepository sonarProjectRepository, CodeQualityRepository codeQualityRepository,
                           CollectorRepository collectorRepository,
                           SonarClientSelector sonarClientSelector) {
        this.sonarProjectRepository = sonarProjectRepository;
        this.codeQualityRepository = codeQualityRepository;
        this.collectorRepository = collectorRepository;
        this.sonarClientSelector = sonarClientSelector;
    }

    @RequestMapping(value = "/refresh", method = GET, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> refresh(@Valid String projectName, @Valid String projectKey, @Valid String instanceUrl) {
        if (Objects.isNull(instanceUrl)) {
            return ResponseEntity.status(HttpStatus.OK).body("sonar instanceUrl is invalid");
        }
        Double version = this.sonarClientSelector.getSonarVersion(instanceUrl);
        SonarClient sonarClient = this.sonarClientSelector.getSonarClient(version);

        Collector sonarCollector = collectorRepository.findByName("Sonar");
        if (Objects.isNull(sonarCollector)) {
            return ResponseEntity.status(HttpStatus.OK).body("sonar collector not found");
        }
        SonarProject projectToUpdate = null;
        if (Objects.nonNull(projectName)) {
            projectToUpdate = getLatestProject(sonarCollector, instanceUrl, projectName);
        } else {
            if (Objects.nonNull(projectKey)) {
                projectToUpdate = sonarClient.getProject(projectKey, instanceUrl);
                projectToUpdate = getLatestProject(sonarCollector, projectToUpdate.getInstanceUrl(), projectToUpdate.getProjectName());
            }
        }
        if (Objects.isNull(projectToUpdate)) {
            return ResponseEntity.status(HttpStatus.OK).body(String.format("no records found for projectName=%s projectKey=%s instanceUrl=%s",
                    Objects.toString(projectName,""), Objects.toString(projectKey, ""), instanceUrl));
        }
        updateCodeQualityData(sonarCollector, sonarClient, projectToUpdate);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(String.format("Successfully refreshed sonar project : projectName=%s projectKey=%s", Objects.toString(projectName, ""),
                        Objects.toString(projectKey, "")));
    }

    private SonarProject getLatestProject(Collector sonarCollector, String instanceUrl, String projectName) {
        List<SonarProject> sonarProjects = sonarProjectRepository.findSonarProjectsByProjectName(sonarCollector.getId(), instanceUrl, projectName);
        return CollectionUtils.isNotEmpty(sonarProjects) ?
                sonarProjects.stream().sorted(Comparator.comparing(SonarProject::getLastUpdated).reversed()).findFirst().get() : null;
    }

    private void updateCodeQualityData(Collector sonarCollector, SonarClient sonarClient, SonarProject project) {
        try {
            CodeQuality codeQuality = sonarClient.currentCodeQuality(project);
            if (codeQuality != null) {
                project.setLastUpdated(System.currentTimeMillis());
                project.setCollectorId(sonarCollector.getId());
                sonarProjectRepository.save(project);
                codeQuality.setCollectorItemId(project.getId());
                codeQuality.setTimestamp(System.currentTimeMillis());
                codeQualityRepository.save(codeQuality);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                project.setEnabled(false);
                project.setLastUpdated(System.currentTimeMillis());
                CollectionError error = new CollectionError("404", "disabled as the project no longer exists in Sonar");
                project.getErrors().add(error);
                sonarProjectRepository.save(project);
                LOG.info(String.format("Disabled as a result of HTTPStatus.NOT_FOUND, projectName=%s instanceUrl=%s",
                        project.getProjectName(), project.getInstanceUrl()));
            } else {
                LOG.error(e.getStackTrace());
            }
        } catch (ParseException parseEx) {
            CollectionError error = new CollectionError("500", parseEx.getMessage());
            project.getErrors().add(error);
            sonarProjectRepository.save(project);
            LOG.error(parseEx);
        }
    }
}
