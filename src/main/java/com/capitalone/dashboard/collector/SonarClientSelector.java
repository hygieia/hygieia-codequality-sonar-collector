package com.capitalone.dashboard.collector;


import java.net.URI;

import com.capitalone.dashboard.client.RestOperationsSupplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

@Component
public class SonarClientSelector {
	private static final Log LOG = LogFactory.getLog(SonarClientSelector.class);

	private static final String URL_VERSION_RESOURCE = "/api/server/version";

    private DefaultSonar8Client sonar8Client;
    private DefaultSonar6Client sonar6Client;
    private DefaultSonar56Client sonar56Client;
    private DefaultSonarClient sonarClient;
    private RestOperations rest;
    
    @Autowired
    public SonarClientSelector(
            DefaultSonar8Client sonar8Client, @Qualifier("DefaultSonar6Client") DefaultSonar6Client sonar6Client, DefaultSonar56Client sonar56Client,
            @Qualifier("DefaultSonarClient") DefaultSonarClient sonarClient,
            RestOperationsSupplier restOperationsSupplier) {

        this.sonar8Client = sonar8Client;
        this.sonar6Client = sonar6Client;
        this.sonar56Client = sonar56Client;
        this.sonarClient = sonarClient;
        this.rest = restOperationsSupplier.get();
    }
    
    public Double getSonarVersion(String instanceUrl){
    	Double version = 7.9;
    	try {
    	    ResponseEntity<String> versionResponse = rest.exchange(URI.create(instanceUrl + URL_VERSION_RESOURCE), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
    	    if(!versionResponse.getBody().isEmpty()) {
    	        if(StringUtils.countOccurrencesOf(versionResponse.getBody(), ".") > 1) {
    	            version = Double.parseDouble(versionResponse.getBody().substring(0, 3));
    	        } else {
    	            version = Double.parseDouble(versionResponse.getBody());
    	        }
    	    }    
    	} catch (RestClientException e) {
    		LOG.info("Rest exception occurred at fetching sonar version - " + e.getMessage());
    	}
        return version;
    }

    public SonarClient getSonarClient(Double version) {
        LOG.info(String.format("getSonarClient version=%s", version));
        if(version != null && version == 5.6){
          return sonar56Client;
        }
        if (version != null && version >= 8.0){
            return sonar8Client;
        }
        return ((version == null) || (version < 6.3)) ? sonarClient : sonar6Client;
    }
}
