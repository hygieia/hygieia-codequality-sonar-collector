package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.Component;
import com.capitalone.dashboard.model.ConfigHistOperationType;
import com.capitalone.dashboard.model.SonarCollector;
import com.capitalone.dashboard.repository.CodeQualityRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.SonarCollectorRepository;
import com.capitalone.dashboard.repository.SonarProfileRepostory;
import com.capitalone.dashboard.repository.SonarProjectRepository;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SonarCollectorTaskTest {

    @InjectMocks private SonarCollectorTask task;
    @Mock private SonarCollectorRepository sonarCollectorRepository;
    @Mock private SonarProjectRepository sonarProjectRepository;
    @Mock private CodeQualityRepository codeQualityRepository;
    @Mock private SonarProfileRepostory sonarProfileRepostory;

    @Mock private SonarSettings sonarSettings;
    @Mock private ComponentRepository dbComponentRepository;
    @Mock private SonarClientSelector sonarClientSelector;
    @Mock private DefaultSonarClient defaultSonarClient;
    @Mock private DefaultSonar6Client defaultSonar6Client;
    @Mock private DefaultSonar8Client defaultSonar8Client;

    private static final String SERVER1 = "server1";
    private static final String SERVER2 = "server2";
    private static final String METRICS1 = "nloc";
    private static final String METRICS2 = "nloc,violations";
    private static final Double VERSION43 = 4.3;
    private static final Double VERSION54 = 5.4;
    private static final Double VERSION63 = 6.3;
    private static final Double VERSION83 = 8.3;
    private static final String NICENAME1 = "niceName1";
    private static final String NICENAME2 = "niceName2";
    private static final String QUALITYPROFILE = "cs-default-donotmodify-89073";
    private static final String LANGUAGE = "java";
    private JSONArray qualityProfiles = new JSONArray();
    private JSONArray qualityProfiles83 = new JSONArray();
    private JSONArray profileConfigurationChanges = new JSONArray();
    private JSONObject qualityProfile = new JSONObject();
    private JSONObject qualityProfile83 = new JSONObject();
    private JSONObject profileConfigurationChange = new JSONObject();
    ConfigHistOperationType operation = ConfigHistOperationType.CHANGED;
    
    @BeforeEach
    public void setup() throws ParseException{
    	qualityProfile.put("key", QUALITYPROFILE);
    	qualityProfile.put("name", "Default-DoNotModify");
    	qualityProfiles.add(qualityProfile);

        qualityProfile83.put("key", QUALITYPROFILE);
        qualityProfile83.put("name", "Default-DoNotModify");
        qualityProfile83.put("language", LANGUAGE);
        qualityProfiles83.add(qualityProfile83);

        profileConfigurationChange.put("authorName", "foo");
    	profileConfigurationChange.put("authorLogin", "bar");
    	profileConfigurationChange.put("date", "2017-10-05T13:57:40+0000");
    	profileConfigurationChange.put("action", "DEACTIVATED");
    	profileConfigurationChanges.add(profileConfigurationChange);
    	
//    	Mockito.doReturn(qualityProfiles).when(defaultSonarClient).getQualityProfiles(SERVER1);
//    	
//    	Mockito.doReturn(profileConfigurationChanges).when(defaultSonarClient).getQualityProfileConfigurationChanges(SERVER1, qualityProfile);
//
//    	Mockito.doReturn(qualityProfiles).when(defaultSonar6Client).getQualityProfiles(SERVER1);
//    	Mockito.doReturn(qualityProfiles).when(defaultSonar6Client).getQualityProfiles(SERVER2);
//
//    	Mockito.doReturn(profileConfigurationChanges).when(defaultSonar6Client).getQualityProfileConfigurationChanges(SERVER1, qualityProfile);
//    	Mockito.doReturn(profileConfigurationChanges).when(defaultSonar6Client).getQualityProfileConfigurationChanges(SERVER2, qualityProfile);
//
//        Mockito.doReturn(qualityProfiles83).when(defaultSonar8Client).getQualityProfiles(SERVER1);
//        Mockito.doReturn(qualityProfiles83).when(defaultSonar8Client).getQualityProfiles(SERVER2);
//
//        Mockito.doReturn(profileConfigurationChanges).when(defaultSonar8Client).getQualityProfileConfigurationChanges(SERVER1, qualityProfile83);
//        Mockito.doReturn(profileConfigurationChanges).when(defaultSonar8Client).getQualityProfileConfigurationChanges(SERVER2, qualityProfile83);
    }

    @Test
    public void collectEmpty() throws Exception {
        //when(dbComponentRepository.findAll()).thenReturn(components());
        task.collect(new SonarCollector());
        verifyNoInteractions(sonarClientSelector, codeQualityRepository);
    }

    @Test
    public void collectOneServer43() throws Exception {
//    	when(dbComponentRepository.findAll()).thenReturn(components());
        when(sonarClientSelector.getSonarVersion(SERVER1)).thenReturn(VERSION43);
//        when(sonarSettings.getServers()).thenReturn(Arrays.asList(SERVER1));
        when(sonarSettings.getUsernames()).thenReturn(Arrays.asList("bob"));
        when(sonarSettings.getPasswords()).thenReturn(Arrays.asList("matrix"));

        when(sonarClientSelector.getSonarClient(VERSION43)).thenReturn(defaultSonarClient);

        task.collect(collectorWithOneServer());

        verify(sonarClientSelector).getSonarClient(VERSION43);
    }

    @Test
    public void collectOneServer54() throws Exception {
//        when(dbComponentRepository.findAll()).thenReturn(components());
        when(sonarClientSelector.getSonarVersion(SERVER1)).thenReturn(VERSION54);

//        when(sonarSettings.getServers()).thenReturn(Arrays.asList(SERVER1));
        when(sonarSettings.getUsernames()).thenReturn(Arrays.asList("robert"));
        when(sonarSettings.getPasswords()).thenReturn(Arrays.asList("k"));

        when(sonarClientSelector.getSonarClient(VERSION54)).thenReturn(defaultSonar6Client);

        task.collect(collectorWithOneServer());

        verify(sonarClientSelector).getSonarClient(VERSION54);
        verify(defaultSonar6Client).getQualityProfiles(SERVER1);
//        verify(defaultSonar6Client).retrieveProfileAndProjectAssociation(SERVER1, qualityProfile);
//        verify(defaultSonar6Client).getQualityProfileConfigurationChanges(SERVER1, qualityProfile);
    }

    @Test
    public void collectOneServer63() throws Exception {
//        when(dbComponentRepository.findAll()).thenReturn(components());
        when(sonarClientSelector.getSonarVersion(SERVER1)).thenReturn(VERSION63);

//        when(sonarSettings.getServers())
//            .thenReturn(Arrays.asList(SERVER1))
//            .thenReturn(Arrays.asList(SERVER1));
        when(sonarSettings.getUsernames()).thenReturn(Arrays.asList("yes"));
        when(sonarSettings.getPasswords()).thenReturn(Arrays.asList("4kkpt"));

        when(sonarClientSelector.getSonarClient(VERSION63)).thenReturn(defaultSonar6Client);

        task.collect(collectorWithOneServer());

        verify(sonarClientSelector).getSonarClient(VERSION63);
        verify(defaultSonar6Client).getQualityProfiles(SERVER1);
//        verify(defaultSonar6Client).retrieveProfileAndProjectAssociation(SERVER1, qualityProfile);
//        verify(defaultSonar6Client).getQualityProfileConfigurationChanges(SERVER1, qualityProfile);
    }

    @Test
    public void collectOneServer83() throws Exception {
        //when(dbComponentRepository.findAll()).thenReturn(components());
        when(sonarClientSelector.getSonarVersion(SERVER1)).thenReturn(VERSION83);

//        when(sonarSettings.getServers())
//                .thenReturn(Arrays.asList(SERVER1))
//                .thenReturn(Arrays.asList(SERVER1));
        when(sonarSettings.getUsernames()).thenReturn(Arrays.asList("yes"));
        when(sonarSettings.getPasswords()).thenReturn(Arrays.asList("4kkpt"));

        when(sonarClientSelector.getSonarClient(VERSION83)).thenReturn(defaultSonar8Client);

        task.collect(collectorWithOneServer());

        verify(sonarClientSelector).getSonarClient(VERSION83);
        verify(defaultSonar8Client).getQualityProfiles(SERVER1);
        //verify(defaultSonar8Client).retrieveProfileAndProjectAssociation(SERVER1, qualityProfile83);
        //verify(defaultSonar8Client).getQualityProfileConfigurationChanges(SERVER1, qualityProfile83);
    }

    @Test
    public void collectTwoServer43And54() throws Exception {
//        when(dbComponentRepository.findAll()).thenReturn(components());
        when(sonarClientSelector.getSonarVersion(SERVER1)).thenReturn(VERSION43);
        when(sonarClientSelector.getSonarVersion(SERVER2)).thenReturn(VERSION54);
//        when(sonarSettings.getServers()).thenReturn(Arrays.asList(SERVER1, SERVER2));
        when(sonarSettings.getUsernames()).thenReturn(Arrays.asList("bob", "bob"));
        when(sonarSettings.getPasswords()).thenReturn(Arrays.asList("k", "l"));
        when(sonarClientSelector.getSonarClient(VERSION54)).thenReturn(defaultSonar6Client);
        when(sonarClientSelector.getSonarClient(VERSION43)).thenReturn(defaultSonarClient);

        task.collect(collectorWithOnTwoServers());

        verify(sonarClientSelector).getSonarClient(VERSION43);
        verify(sonarClientSelector).getSonarClient(VERSION54);
        
        verify(defaultSonar6Client).getQualityProfiles(SERVER2);
//        verify(defaultSonar6Client).retrieveProfileAndProjectAssociation(SERVER2, qualityProfile);
//        verify(defaultSonar6Client).getQualityProfileConfigurationChanges(SERVER2, qualityProfile);
        
    }

    private ArrayList<com.capitalone.dashboard.model.Component> components() {
        ArrayList<com.capitalone.dashboard.model.Component> cArray = new ArrayList<>();
        com.capitalone.dashboard.model.Component c = new Component();
        c.setId(new ObjectId());
        c.setName("COMPONENT1");
        c.setOwner("JOHN");
        cArray.add(c);
        return cArray;
    }

    private SonarCollector collectorWithOneServer() {
        return SonarCollector.prototype(Collections.singletonList(SERVER1),Collections.singletonList(NICENAME1));
    }

    private SonarCollector collectorWithOnTwoServers() {
        return SonarCollector.prototype(Arrays.asList(SERVER1, SERVER2), Arrays.asList(NICENAME1,NICENAME2));
    }

}
