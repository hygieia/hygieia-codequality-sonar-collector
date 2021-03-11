package com.capitalone.dashboard.collector;
import com.capitalone.dashboard.model.Configuration;
import com.capitalone.dashboard.model.SonarCollector;
import com.capitalone.dashboard.model.SonarProject;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.ConfigHistOperationType;
import com.capitalone.dashboard.model.CollectorItemConfigHistory;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CodeQualityRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.ConfigurationRepository;
import com.capitalone.dashboard.repository.SonarCollectorRepository;
import com.capitalone.dashboard.repository.SonarProfileRepostory;
import com.capitalone.dashboard.repository.SonarProjectRepository;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@Component
public class SonarCollectorTask extends CollectorTask<SonarCollector> {

    private static final Log LOG = LogFactory.getLog(SonarCollectorTask.class);

    private final SonarCollectorRepository sonarCollectorRepository;
    private final SonarProjectRepository sonarProjectRepository;
    private final CodeQualityRepository codeQualityRepository;
    private final SonarProfileRepostory sonarProfileRepostory;
    private final SonarClientSelector sonarClientSelector;
    private final SonarSettings sonarSettings;
    private final ComponentRepository dbComponentRepository;
    private final ConfigurationRepository configurationRepository;
    private AtomicInteger count = new AtomicInteger(0);

    @Autowired
    public SonarCollectorTask(TaskScheduler taskScheduler,
                              SonarCollectorRepository sonarCollectorRepository,
                              SonarProjectRepository sonarProjectRepository,
                              CodeQualityRepository codeQualityRepository,
                              SonarProfileRepostory sonarProfileRepostory,
                              SonarSettings sonarSettings,
                              SonarClientSelector sonarClientSelector,
                              ConfigurationRepository configurationRepository,
                              ComponentRepository dbComponentRepository) {
        super(taskScheduler, "Sonar");
        this.sonarCollectorRepository = sonarCollectorRepository;
        this.sonarProjectRepository = sonarProjectRepository;
        this.codeQualityRepository = codeQualityRepository;
        this.sonarProfileRepostory = sonarProfileRepostory;
        this.sonarSettings = sonarSettings;
        this.sonarClientSelector = sonarClientSelector;
        this.dbComponentRepository = dbComponentRepository;
        this.configurationRepository = configurationRepository;
    }

    @Override
    public SonarCollector getCollector() {

        Configuration config = configurationRepository.findByCollectorName("Sonar");
        // Only use Admin Page server configuration when available
        // otherwise use properties file server configuration
        if (config != null) {
            config.decryptOrEncrptInfo();
            // To clear the username and password from existing run and
            // pick the latest
            sonarSettings.getServers().clear();
            sonarSettings.getUsernames().clear();
            sonarSettings.getPasswords().clear();
            for (Map<String, String> sonarServer : config.getInfo()) {
                sonarSettings.getServers().add(sonarServer.get("url"));
                sonarSettings.getUsernames().add(sonarServer.get("userName"));
                sonarSettings.getPasswords().add(sonarServer.get("password"));
            }
        }

        return SonarCollector.prototype(sonarSettings.getServers(),  sonarSettings.getNiceNames());
    }

    @Override
    public BaseCollectorRepository<SonarCollector> getCollectorRepository() {
        return sonarCollectorRepository;
    }

    @Override
    public String getCron() {
        return sonarSettings.getCron();
    }

    @Override
    public void collect(SonarCollector collector) {
        long start = System.currentTimeMillis();
        int totalProjectCount = 0;
        Set<ObjectId> udId = new HashSet<>();
        udId.add(collector.getId());
        List<SonarProject> existingProjects = sonarProjectRepository.findByCollectorIdIn(udId);
        List<SonarProject> latestProjects = new ArrayList<>();

        if (!CollectionUtils.isEmpty(collector.getSonarServers())) {

            for (int i = 0; i < collector.getSonarServers().size(); i++) {

                String instanceUrl = collector.getSonarServers().get(i);
                logBanner(instanceUrl);

                Double version = sonarClientSelector.getSonarVersion(instanceUrl);
                SonarClient sonarClient = sonarClientSelector.getSonarClient(version);

                String username = getFromListSafely(sonarSettings.getUsernames(), i);
                String password = getFromListSafely(sonarSettings.getPasswords(), i);
                String token = getFromListSafely(sonarSettings.getTokens(), i);
                sonarClient.setServerCredentials(username, password, token);

                List<SonarProject> projects = sonarClient.getProjects(instanceUrl);
                latestProjects.addAll(projects);

                addNewProjects(projects, existingProjects, collector);

                List<SonarProject> enabledProjects = enabledProjects(collector, instanceUrl);
                totalProjectCount = enabledProjects.size();
                refreshData(enabledProjects, sonarClient);

                // Changelog apis do not exist for sonarqube versions under version 5.0
                if (version >= 5.0) {
                    try {
                        fetchQualityProfileConfigChanges(collector,instanceUrl,sonarClient);
                    } catch (Exception e) {
                        LOG.error(e);
                    }
                }

            }
        }
        long end = System.currentTimeMillis();
        long elapsedSeconds = (end - start) / 1000;
        LOG.info(String.format("SonarCollectorTask:collect stop, totalProcessSeconds=%d, totalProjectCount=%d",
                elapsedSeconds, totalProjectCount));

        collector.setLastExecutionRecordCount(totalProjectCount);
        collector.setLastExecutedSeconds(elapsedSeconds);
    }

    private String getFromListSafely(List<String> ls, int index){
        if(CollectionUtils.isEmpty(ls)) {
            return null;
        } else if (ls.size() > index){
            return ls.get(index);
        }
        return null;
    }
    /**
     * Clean up unused sonar collector items
     *
     * @param collector
     *            the {@link SonarCollector}
     */
    private void clean(SonarCollector collector, List<SonarProject> existingProjects) {
        // extract unique collector item IDs from components
        // (in this context collector_items are sonar projects)
        Set<ObjectId> uniqueIDs = StreamSupport.stream(dbComponentRepository.findAll().spliterator(),false)
                .filter( comp -> comp.getCollectorItems() != null && !comp.getCollectorItems().isEmpty())
                .map(comp -> comp.getCollectorItems().get(CollectorType.CodeQuality))
                // keep nonNull List<CollectorItem>
                .filter(Objects::nonNull)
                // merge all lists (flatten) into a stream
                .flatMap(List::stream)
                // keep nonNull CollectorItems
                .filter(ci -> ci != null && ci.getCollectorId().equals(collector.getId()))
                .map(CollectorItem::getId)
                .collect(Collectors.toSet());

        List<SonarProject> stateChangeJobList = new ArrayList<>();

        for (SonarProject job : existingProjects) {
            // collect the jobs that need to change state : enabled vs disabled.
            boolean updated = false;
            if (job.isEnabled()) {
                if (!uniqueIDs.contains(job.getId())) {
                    job.setEnabled(false);
                    updated = true;
                }
            } else {
                if (uniqueIDs.contains(job.getId()) && job.getErrors().size() == 0) {
                    job.setEnabled(true);
                    updated = true;
                }
            }
            if (updated) {
                stateChangeJobList.add(job);
                LOG.info(String.format("ChangeProjectStatus projectName=%s projectId=%s enabled=%s",
                        job.getProjectName(), job.getProjectId(), Boolean.toString(job.isEnabled())));
            }
        }
        if (!CollectionUtils.isEmpty(stateChangeJobList)) {
            sonarProjectRepository.save(stateChangeJobList);
        }
    }

    private void deleteUnwantedJobs(List<SonarProject> latestProjects, List<SonarProject> existingProjects, SonarCollector collector) {
        List<SonarProject> deleteJobList = new ArrayList<>();

        // First delete collector items that are not supposed to be collected anymore because the servers have moved(?)
        for (SonarProject job : existingProjects) {
            if (job.isPushed()) continue; // do not delete jobs that are being pushed via API
            if (!collector.getSonarServers().contains(job.getInstanceUrl()) ||
                    (!job.getCollectorId().equals(collector.getId())) ||
                    (!latestProjects.contains(job))) {
                if(!job.isEnabled()) {
                    LOG.debug("drop deleted sonar project which is disabled "+job.getProjectName());
                    deleteJobList.add(job);
                } else {
                    LOG.debug("drop deleted sonar project which is enabled "+job.getProjectName());
                    // CollectorItem should be removed from components and dashboards first
                    // then the CollectorItem (sonar proj in this case) can be deleted

                    List<com.capitalone.dashboard.model.Component> comps = dbComponentRepository
                            .findByCollectorTypeAndItemIdIn(CollectorType.CodeQuality, Collections.singletonList(job.getId()));

                    for (com.capitalone.dashboard.model.Component c: comps) {
                        c.getCollectorItems().get(CollectorType.CodeQuality).removeIf(collectorItem -> collectorItem.getId().equals(job.getId()));
                        if(CollectionUtils.isEmpty(c.getCollectorItems().get(CollectorType.CodeQuality))){
                            c.getCollectorItems().remove(CollectorType.CodeQuality);
                        }
                    }
                    dbComponentRepository.save(comps);

                    // other collectors also delete the widget but not here
                    // should not remove the code analysis widget
                    // because it is shared by other collectors

                    deleteJobList.add(job);
                }
            }
        }
        if (!CollectionUtils.isEmpty(deleteJobList)) {
            sonarProjectRepository.delete(deleteJobList);
        }
    }

    private void refreshData(List<SonarProject> sonarProjects, SonarClient sonarClient) {
        long start = System.currentTimeMillis();
        count.set(0);
        int updated = 0;
        int disabled = 0;
        for (SonarProject project : sonarProjects) {
            try {
                CodeQuality codeQuality = sonarClient.currentCodeQuality(project);
                if (codeQuality != null && isNewQualityData(project, codeQuality)) {
                    project.setLastUpdated(System.currentTimeMillis());
                    sonarProjectRepository.save(project);
                    codeQuality.setCollectorItemId(project.getId());
                    codeQualityRepository.save(codeQuality);
                    updated++;
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    project.setEnabled(false);
                    project.setLastUpdated(System.currentTimeMillis());
                    CollectionError error = new CollectionError("404", "disabled as the project no longer exists in Sonar");
                    project.getErrors().add(error);
                    sonarProjectRepository.save(project);
                    LOG.info("Disabled as a result of HTTPStatus.NOT_FOUND, projectName=" + project.getProjectName()
                            + ", projectId=" + project.getProjectId());
                    disabled++;
                } else {
                    LOG.error(e.getStackTrace());
                }
            } catch (ParseException parseEx) {
                CollectionError error = new CollectionError("500", parseEx.getMessage());
                project.getErrors().add(error);
                sonarProjectRepository.save(project);
                LOG.error(parseEx);
            }
            count.getAndIncrement();
        }
        LOG.info("refreshData updated, total=" + count.get() + ", updated=" + updated + ", disabled=" + disabled + ", timeTaken=" + start);
    }

    private void fetchQualityProfileConfigChanges(SonarCollector collector,String instanceUrl,SonarClient sonarClient) throws org.json.simple.parser.ParseException{
        JSONArray qualityProfiles = sonarClient.getQualityProfiles(instanceUrl);
        JSONArray sonarProfileConfigurationChanges = new JSONArray();

        for (Object qualityProfile : qualityProfiles ) {
            JSONObject qualityProfileJson = (JSONObject) qualityProfile;

            List<String> sonarProjects = sonarClient.retrieveProfileAndProjectAssociation(instanceUrl,qualityProfileJson);
            if (sonarProjects != null){
                sonarProfileConfigurationChanges = sonarClient.getQualityProfileConfigurationChanges(instanceUrl,qualityProfileJson);
                addNewConfigurationChanges(collector,sonarProfileConfigurationChanges);
            }
        }
    }

    private void addNewConfigurationChanges(SonarCollector collector,JSONArray sonarProfileConfigurationChanges){
        ArrayList<CollectorItemConfigHistory> profileConfigChanges = new ArrayList<>();

        for (Object configChange : sonarProfileConfigurationChanges) {
            JSONObject configChangeJson = (JSONObject) configChange;
            CollectorItemConfigHistory profileConfigChange = new CollectorItemConfigHistory();
            Map<String,Object> changeMap = new HashMap<>();

            profileConfigChange.setCollectorItemId(collector.getId());
            profileConfigChange.setUserName((String) configChangeJson.get("authorName"));
            profileConfigChange.setUserID((String) configChangeJson.get("authorLogin") );
            changeMap.put("event", configChangeJson);

            profileConfigChange.setChangeMap(changeMap);

            ConfigHistOperationType operation = determineConfigChangeOperationType((String)configChangeJson.get("action"));
            profileConfigChange.setOperation(operation);


            long timestamp = convertToTimestamp((String) configChangeJson.get("date"));
            profileConfigChange.setTimestamp(timestamp);

            if (isNewConfig(collector.getId(),(String) configChangeJson.get("authorLogin"),operation,timestamp)) {
                profileConfigChanges.add(profileConfigChange);
            }
        }
        sonarProfileRepostory.save(profileConfigChanges);
    }

    private Boolean isNewConfig(ObjectId collectorId,String authorLogin,ConfigHistOperationType operation,long timestamp) {
        List<CollectorItemConfigHistory> storedConfigs = sonarProfileRepostory.findProfileConfigChanges(collectorId, authorLogin,operation,timestamp);
        return storedConfigs.isEmpty();
    }

    private List<SonarProject> enabledProjects(SonarCollector collector, String instanceUrl) {
        return sonarProjectRepository.findEnabledProjects(collector.getId(), instanceUrl);
    }

    private void addNewProjects(List<SonarProject> projects, List<SonarProject> existingProjects, SonarCollector collector) {
        long start = System.currentTimeMillis();
        int newCount = 0;
        int updatedCount = 0;
        List<SonarProject> newProjects = new ArrayList<>();
        List<SonarProject> updateProjects = new ArrayList<>();
        for (SonarProject project : projects) {
            String niceName = getNiceName(project,collector);
            // TODO: the algorithm in this loop is of n^2, need to optimize
            if (!existingProjects.contains(project)) {
                project.setCollectorId(collector.getId());
                project.setEnabled(false);
                project.setDescription(project.getProjectName());
                project.setNiceName(niceName);
                newProjects.add(project);
                LOG.info(String.format("NewProject projectName=%s projectId=%s enabled=false",
                        project.getProjectName(), project.getProjectId()));
                newCount++;
            }else{
                int[] indexes = IntStream.range(0,existingProjects.size()).filter(i-> existingProjects.get(i).equals(project)).toArray();
                for (int index :indexes) {
                    SonarProject s = existingProjects.get(index);
                    if(Objects.isNull(s.getProjectId())){
                        LOG.info("ProjectId is null for sonar project="+s.getProjectName());
                    }
                    if ((Objects.nonNull(s.getProjectId()) && !s.getProjectId().equals(project.getProjectId())) || !StringUtils.equals(s.getNiceName(),project.getNiceName())) {
                        LOG.info(String.format("UpdatedProject projectName=%s projectId=%s enabled=%s",
                                project.getProjectName(), s.getProjectId(), Boolean.toString(s.isEnabled())));
                        if (s.getErrors().size() > 0) {
                            s.getErrors().clear();
                        }
                        s.setProjectId(project.getProjectId());
                        if (StringUtils.isEmpty(s.getNiceName())) {
                            s.setNiceName(niceName);
                        }
                        updateProjects.add(s);
                        updatedCount++;
                    }
                }
            }
        }
        //save all in one shot
        if (!CollectionUtils.isEmpty(newProjects)) {
            sonarProjectRepository.save(newProjects);
        }
        if (!CollectionUtils.isEmpty(updateProjects)) {
            sonarProjectRepository.save(updateProjects);
        }
        LOG.info(String.format("addNewProjects projectsInSonar=%d existingProjects=%d new=%d updated=%d timeUsed=%d",
                projects.size(), existingProjects.size(), newCount, updatedCount, System.currentTimeMillis()-start));
    }

    private String getNiceName(SonarProject project, SonarCollector sonarCollector){

        if (org.springframework.util.CollectionUtils.isEmpty(sonarCollector.getSonarServers())) return "";
        List<String> servers = sonarCollector.getSonarServers();
        List<String> niceNames = sonarCollector.getNiceNames();
        if (org.springframework.util.CollectionUtils.isEmpty(niceNames)) return "";
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).equalsIgnoreCase(project.getInstanceUrl()) && (niceNames.size() > i)) {
                return niceNames.get(i);
            }
        }
        return "";

    }

    @SuppressWarnings("unused")
    private boolean isNewProject(SonarCollector collector, SonarProject application) {
        return sonarProjectRepository.findSonarProject(
                collector.getId(), application.getInstanceUrl(), application.getProjectId()) == null;
    }

    private boolean isNewQualityData(SonarProject project, CodeQuality codeQuality) {
        return codeQualityRepository.findByCollectorItemIdAndTimestamp(
                project.getId(), codeQuality.getTimestamp()) == null;
    }

    private long convertToTimestamp(String date) {

        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");
        DateTime dt = formatter.parseDateTime(date);

        return new DateTime(dt).getMillis();
    }

    private ConfigHistOperationType determineConfigChangeOperationType(String changeAction){
        switch (changeAction) {

            case "DEACTIVATED":
                return ConfigHistOperationType.DELETED;

            case "ACTIVATED":
                return ConfigHistOperationType.CREATED;
            default:
                return ConfigHistOperationType.CHANGED;
        }
    }
}
