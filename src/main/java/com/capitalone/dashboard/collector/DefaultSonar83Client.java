package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestUserInfo;
import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.util.SonarDashboardUrl;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class DefaultSonar83Client extends DefaultSonar6Client {
    private static final Log LOG = LogFactory.getLog(DefaultSonar83Client.class);

    protected static final String URL_QUALITY_PROFILE_CHANGES = "/api/qualityprofiles/changelog?profileKey=%s&language=%s";

    @Autowired
    public DefaultSonar83Client(RestClient restClient, SonarSettings settings) {
        super(restClient, settings);
    }

    @Override
    public JSONArray getQualityProfileConfigurationChanges(String instanceUrl, String qualityProfile, String language) throws ParseException{
        String url = String.format(instanceUrl + URL_QUALITY_PROFILE_CHANGES, qualityProfile, language);
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
