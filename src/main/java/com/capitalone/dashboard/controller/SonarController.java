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
                    updateCodeQualityData(projectToRefresh);
                    return response("successfully refreshed sonar project");
                }
                return response("unable to refresh sonar project");
            }
            return response("sonar collector not found");
        }
        return response("sonar instance url is invalid");
    }

    private void gatherParams(String projectName, String projectKey, String instanceUrl) {
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.instanceUrl = instanceUrl;
    }

    private ResponseEntity<String> response(String message) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(String.format(message + " - projectName=%s projectKey=%s instanceUrl=%s ",
                        Objects.toString(this.projectName, ""), Objects.toString(this.projectKey, ""), this.instanceUrl));
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

    private void updateCodeQualityData(SonarProject project) {
        try {
            CodeQuality codeQuality = this.sonarClient.currentCodeQuality(project);
            if (codeQuality != null) {
                project.setLastUpdated(System.currentTimeMillis());
                project.setCollectorId(this.collector.getId());
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
