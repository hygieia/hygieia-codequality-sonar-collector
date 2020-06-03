package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class DefaultSonar8Client extends DefaultSonar6Client {
    private static final Log LOG = LogFactory.getLog(DefaultSonar8Client.class);

    protected static final String URL_QUALITY_PROFILE_CHANGES = "/api/qualityprofiles/changelog?qualityProfile=%s&language=%s";

    @Autowired
    public DefaultSonar8Client(RestClient restClient, SonarSettings settings) {
        super(restClient, settings);
    }

    @Override
    public JSONArray getQualityProfileConfigurationChanges(String instanceUrl, JSONObject qualityProfile) throws ParseException{
        String url = String.format(instanceUrl + URL_QUALITY_PROFILE_CHANGES, qualityProfile.get("name"), qualityProfile.get("language"));
        try {
            JSONArray qualityProfileConfigChanges = this.parseAsArray(url, "events");
            return qualityProfileConfigChanges;
        } catch (ParseException e) {
            LOG.error("Could not parse response from: " + url, e);
            throw e;
        } catch (RestClientException rce) {
            LOG.error(rce);
            throw rce;
        }
    }

}
