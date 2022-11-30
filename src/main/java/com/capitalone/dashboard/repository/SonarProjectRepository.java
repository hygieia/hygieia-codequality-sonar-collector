package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.SonarProject;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface SonarProjectRepository extends BaseCollectorItemRepository<SonarProject> {

    @Query(value="{ 'collectorId' : ?0, 'options.instanceUrl' : ?1, 'options.projectId' : ?2}")
    SonarProject findSonarProject(ObjectId collectorId, String instanceUrl, String projectId);

    @Query(value="{ 'collectorId' : ?0, 'options.instanceUrl' : ?1, 'options.projectName' : ?2}")
    List<SonarProject> findSonarProjectsByProjectName(ObjectId collectorId, String instanceUrl, String projectName);

    @Query(value="{ 'collectorId' : ?0, 'options.instanceUrl' : ?1, 'enabled' : true}")
    List<SonarProject> findEnabledProjects(ObjectId collectorId, String instanceUrl);
}
