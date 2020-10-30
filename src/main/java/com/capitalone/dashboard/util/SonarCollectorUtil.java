package com.capitalone.dashboard.util;

import com.capitalone.dashboard.collector.SonarClient;
import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.SonarProject;
import com.capitalone.dashboard.repository.CodeQualityRepository;
import com.capitalone.dashboard.repository.SonarProjectRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class SonarCollectorUtil {

    private static final Log LOG = LogFactory.getLog(SonarCollectorUtil.class);

    private static SonarProjectRepository sonarProjectRepository;
    private static CodeQualityRepository codeQualityRepository;

    public SonarCollectorUtil(SonarProjectRepository sonarProjectRepository, CodeQualityRepository codeQualityRepository) {
        this.sonarProjectRepository = sonarProjectRepository;
        this.codeQualityRepository = codeQualityRepository;
    }


    public static void updateCodeQualityData(Collector sonarCollector, SonarClient sonarClient, SonarProject project) {
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
