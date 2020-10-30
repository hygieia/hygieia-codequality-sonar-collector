package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestUserInfo;
import com.capitalone.dashboard.model.CodeQualityMetric;
import com.capitalone.dashboard.model.CodeQualityMetricStatus;
import com.capitalone.dashboard.model.CodeQualityType;
import com.capitalone.dashboard.util.SonarDashboardUrl;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.capitalone.dashboard.model.CodeQuality;
import com.capitalone.dashboard.model.SonarProject;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component("DefaultSonar6Client")
public class DefaultSonar6Client implements SonarClient {
    private static final Log LOG = LogFactory.getLog(DefaultSonar6Client.class);
    private static final String URL_RESOURCES = "/api/components/search?qualifiers=TRK&ps=500";
    private static final String URL_RESOURCES_AUTHENTICATED = "/api/projects/search?ps=500";
    private static final String URL_RESOURCE_DETAILS = "/api/measures/component?format=json&componentId=%s&metricKeys=%s&includealerts=true";
    static final String URL_PROJECT_ANALYSES = "/api/project_analyses/search?project=%s";
    private static final String URL_PROJECT_INFO = "%s/api/components/show?component=%s";
    private static final String URL_QUALITY_PROFILES = "/api/qualityprofiles/search";
    private static final String URL_QUALITY_PROFILE_PROJECT_DETAILS = "/api/qualityprofiles/projects?key=";
    private static final String URL_QUALITY_PROFILE_CHANGES = "/api/qualityprofiles/changelog?profileKey=";
    private static final String DEFAULT_METRICS = "ncloc,violations,new_vulnerabilities,critical_violations,major_violations,blocker_violations,tests,test_success_density,test_errors,test_failures,coverage,line_coverage,sqale_index,alert_status,quality_gate_details";
    protected final String metrics;

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String ID = "id";
    protected static final String NAME = "name";
    protected static final String KEY = "key";
    protected static final String METRIC = "metric";
    protected static final String MSR = "measures";
    protected static final String VALUE = "value";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_ALERT = "ALERT";
    private static final String DATE = "date";
    private static final String EVENTS = "events";
    private static final String COMPONENT = "component";

    protected final RestClient restClient;
    protected RestUserInfo userInfo = new RestUserInfo("","");

    private static final String MINUTES_FORMAT = "%smin";
    private static final String HOURS_FORMAT = "%sh";
    private static final String DAYS_FORMAT = "%sd";
    private static final int HOURS_IN_DAY = 8;
    private static final int PAGE_SIZE=500;

    @Autowired
    public DefaultSonar6Client(RestClient restClient, SonarSettings settings) {
        this.restClient = restClient;

        // override default sonar metrics to fetch via properties file settings
        if (!StringUtils.isEmpty(settings.getMetrics63andAbove())) {
            metrics = settings.getMetrics63andAbove();
        } else {
            metrics = DEFAULT_METRICS;
        }
    }

    @Override
    public void setServerCredentials(String username, String password, String token) {
        // use token when given
        if (StringUtils.isNotBlank(token)) {
            this.userInfo.setToken(token);
            this.userInfo.setUserId(null);
            this.userInfo.setPassCode(null);
        }

        // but username and password override token
        if(StringUtils.isNotBlank(username)){
            this.userInfo = new RestUserInfo(username, password);
        }

        if (StringUtils.isNotBlank(token)
                && StringUtils.isNotBlank(username)) {
            LOG.error("Only one mode of authentication is needed. Either token or username/password. " +
                    "Both modes were detected. Using username/password");
        }
    }

    @Override
    public List<SonarProject> getProjects(String instanceUrl) {
        List<SonarProject> projects = new ArrayList<>();
        String url = "";
        // take authenticated route
        if(Objects.nonNull(userInfo.getToken())){
            url = instanceUrl +  URL_RESOURCES_AUTHENTICATED;
        }else{
            url = instanceUrl + URL_RESOURCES;
        }

        try {
            JSONArray jsonArray = getProjectsWithPaging(url);
            for (Object obj : jsonArray) {
                JSONObject prjData = (JSONObject) obj;

                SonarProject project = parseSonarProject(instanceUrl, prjData);
                projects.add(project);
            }

        } catch (ParseException e) {
            LOG.error("Could not parse response from: " + url, e);
        } catch (RestClientException rce) {
            LOG.error(rce);
        }

        return projects;
    }

    protected SonarProject parseSonarProject(String instanceUrl, JSONObject prjData) {
        SonarProject project = new SonarProject();
        project.setInstanceUrl(instanceUrl);
        project.setProjectId(str(prjData, ID));
        project.setProjectName(str(prjData, NAME));
        return project;
    }

    private JSONArray getProjectsWithPaging(String url) throws ParseException {
        String key = "components";
        Long totalRecords = getTotalCount(parseJsonObject(url, "paging"));
        int pages = (int) Math.ceil((double)totalRecords / PAGE_SIZE);
        JSONArray jsonArray = new JSONArray();
        jsonArray = totalRecords > PAGE_SIZE ? getProjects(url, key, pages, jsonArray): getProjects(url, key, jsonArray);
        return jsonArray;
    }

    private JSONArray getProjects(String url, String key, JSONArray jsonArray) throws ParseException {
        jsonArray.addAll(parseAsArray(url, key));
        return jsonArray;
    }

    private JSONArray getProjects(String url, String key, int pages, JSONArray jsonArray) throws ParseException {
        if(Objects.isNull(userInfo.getToken())){
            pagingUnAuthenticated(url, key, pages, jsonArray);
        }else{
            for (int start=1;start<=pages;start++){
                getProjects(url, key, jsonArray, start);
            }
        }
        return  jsonArray;
    }

    private void pagingUnAuthenticated(String url, String key, int pages, JSONArray jsonArray) throws ParseException {
        int maxPages = 20;
        if(pages <= maxPages) {
            maxPages = pages;
        }
        for (int start=1;start<=maxPages;start++){
            getProjects(url, key, jsonArray, start);
        }
    }

    private void getProjects(String url, String key, JSONArray jsonArray, int pageNumber) throws ParseException {
        String urlFinal = url+"&p="+pageNumber;
        jsonArray.addAll(parseAsArray(urlFinal, key));
    }

    public SonarProject getProject(String projectKey, String instanceUrl) {
        String url = String.format(URL_PROJECT_INFO, instanceUrl, projectKey);
        SonarProject project = null;
        try {
            JSONObject component = child(getResponse(url), COMPONENT);
            if (component != null) {
                project =  parseSonarProject(instanceUrl, component);
                project.setEnabled(false);
                project.setDescription(project.getProjectName());
            }
        } catch (ParseException e) {
            LOG.error("Could not parse response from: " + url, e);
        } catch (RestClientException rce) {
            LOG.error(rce);
        }
        return project;
    }

    @Override
    public CodeQuality currentCodeQuality(SonarProject project) throws HttpClientErrorException, ParseException {
        String url = String.format(
                project.getInstanceUrl() + getResourceDetailsUrl(), project.getProjectId(), metrics);

        ResponseEntity<String> response = restClient.makeRestCallGet(url,setHeaders(userInfo) );
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(response.getBody());

        if (jsonObject != null) {
            JSONObject prjData = (JSONObject) jsonObject.get("component");
            String key = str(prjData, KEY);

            CodeQuality codeQuality = new CodeQuality();
            codeQuality.setType(CodeQualityType.StaticAnalysis);
            codeQuality.setName(str(prjData, NAME));
            codeQuality.setUrl(new SonarDashboardUrl(project.getInstanceUrl(), key).toString());

            updateCodeQualityProjectAnalysis(codeQuality, project, key);

            List<CodeQualityMetric> metrics = parseCodeQualityMetrics((JSONArray) prjData.get(MSR));
            codeQuality.getMetrics().addAll(metrics);

            return codeQuality;
        }
        return null;
    }

    protected String getResourceDetailsUrl() {
        return URL_RESOURCE_DETAILS;
    }

    protected List<CodeQualityMetric> parseCodeQualityMetrics(JSONArray measures) {
        List<CodeQualityMetric> metrics = new ArrayList<>();
        for (Object metricObj : measures) {
            JSONObject metricJson = (JSONObject) metricObj;

            CodeQualityMetric metric = new CodeQualityMetric(str(metricJson, METRIC));
            metric.setValue(str(metricJson, VALUE));
            if (metric.getName().equals("sqale_index")) {
                metric.setFormattedValue(format(str(metricJson, VALUE)));
            } else if (strSafe(metricJson, VALUE).indexOf(".") > 0) {
                metric.setFormattedValue(str(metricJson, VALUE) + "%" );
            } else if (strSafe(metricJson, VALUE).matches("\\d+")) {
                metric.setFormattedValue(String.format("%,d", integer(metricJson, VALUE)));
            } else {
                metric.setFormattedValue(str(metricJson, VALUE));
            }
            metrics.add(metric);
        }
        return metrics;
    }

    protected void updateCodeQualityProjectAnalysis(CodeQuality codeQuality, SonarProject project, String key) throws ParseException {
        String url = String.format(
                project.getInstanceUrl() + URL_PROJECT_ANALYSES, key);
        JSONArray jsonArray = parseAsArray(url, "analyses");
        if(jsonArray!=null && !jsonArray.isEmpty()) {
            JSONObject prjLatestData = (JSONObject) jsonArray.get(0);
            codeQuality.setTimestamp(timestamp(prjLatestData, DATE));
            for (Object eventObj : (JSONArray) prjLatestData.get(EVENTS)) {
                JSONObject eventJson = (JSONObject) eventObj;

                if (strSafe(eventJson, "category").equals("VERSION")) {
                    codeQuality.setVersion(str(eventJson, NAME));
                }
            }
        }
    }

    public List<String> retrieveProfileAndProjectAssociation(String instanceUrl,JSONObject qualityProfile) throws ParseException{
        List<String> projects = new ArrayList<>();
        String url = instanceUrl + URL_QUALITY_PROFILE_PROJECT_DETAILS + qualityProfile.get("key");
        try {
            JSONArray associatedProjects = this.parseAsArray(url, "results");
            if (!CollectionUtils.isEmpty(associatedProjects)) {
                for (Object project : associatedProjects) {
                    JSONObject projectJson = (JSONObject) project;
                    String projectName = (String) projectJson.get("name");
                    projects.add(projectName);
                }
                return projects;
            }
            return null;
        } catch (ParseException e) {
            LOG.error("Could not parse response from: " + url, e);
            throw e;
        } catch (RestClientException rce) {
            LOG.error(rce);
            throw rce;
        }
    }

    public JSONArray getQualityProfiles(String instanceUrl) throws ParseException {
        String url = instanceUrl + URL_QUALITY_PROFILES;
        try {
            JSONArray qualityProfileData = this.parseAsArray(url,"profiles");
            return qualityProfileData;
        } catch (ParseException e) {
            LOG.error("Could not parse response from: " + url, e);
            throw e;
        } catch (RestClientException rce) {
            LOG.error(rce);
            throw rce;
        }
    }

    public JSONArray getQualityProfileConfigurationChanges(String instanceUrl,JSONObject qualityProfile) throws ParseException{
        String url = instanceUrl + URL_QUALITY_PROFILE_CHANGES + qualityProfile.get("key");
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

    protected JSONArray parseAsArray(String url, String key) throws ParseException {
        JSONObject jsonObject = getResponse(url);
        return (JSONArray) jsonObject.get(key);
    }

    private JSONObject parseJsonObject(String url, String key) throws ParseException {
        JSONObject jsonObject = getResponse(url);
        return (JSONObject)jsonObject.get(key);
    }

    private JSONObject getResponse(String url) throws ParseException {
        ResponseEntity<String> response = restClient.makeRestCallGet(url, setHeaders(userInfo));
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(response.getBody());
        LOG.debug(url);
        return jsonObject;
    }

    private long timestamp(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
            try {
                return new SimpleDateFormat(DATE_FORMAT).parse(obj.toString()).getTime();
            } catch (java.text.ParseException e) {
                LOG.error(obj + " is not in expected format " + DATE_FORMAT, e);
            }
        }
        return 0;
    }

    protected String str(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : obj.toString();
    }

    protected JSONObject child(JSONObject json, String key) {
        JSONObject obj = (JSONObject) json.get(key);
        return obj == null ? null : obj;
    }

    protected String strSafe(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? "" : obj.toString();
    }

    @SuppressWarnings("unused")
    protected Integer integer(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : Integer.valueOf(obj.toString());
    }

    @SuppressWarnings("unused")
    private BigDecimal decimal(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : new BigDecimal(obj.toString());
    }

    @SuppressWarnings("unused")
    private Boolean bool(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : Boolean.valueOf(obj.toString());
    }

    @SuppressWarnings("unused")
    protected String format(String duration) {
        Long durationInMinutes = Long.valueOf(duration);
        if (durationInMinutes == 0) {
            return "0";
        }
        boolean isNegative = durationInMinutes < 0;
        Long absDuration = Math.abs(durationInMinutes);

        int days = ((Double) ((double) absDuration / HOURS_IN_DAY / 60)).intValue();
        Long remainingDuration = absDuration - (days * HOURS_IN_DAY * 60);
        int hours = ((Double) (remainingDuration.doubleValue() / 60)).intValue();
        remainingDuration = remainingDuration - (hours * 60);
        int minutes = remainingDuration.intValue();

        return format(days, hours, minutes, isNegative);
    }

    @SuppressWarnings("PMD")
    private static String format(int days, int hours, int minutes, boolean isNegative) {
        StringBuilder message = new StringBuilder();
        if (days > 0) {
            message.append(String.format(DAYS_FORMAT, isNegative ? (-1 * days) : days));
        }
        if (displayHours(days, hours)) {
            addSpaceIfNeeded(message);
            message.append(String.format(HOURS_FORMAT, isNegative && message.length() == 0 ? (-1 * hours) : hours));
        }
        if (displayMinutes(days, hours, minutes)) {
            addSpaceIfNeeded(message);
            message.append(String.format(MINUTES_FORMAT, isNegative && message.length() == 0 ? (-1 * minutes) : minutes));
        }
        return message.toString();
    }

    private static void addSpaceIfNeeded(StringBuilder message) {
        if (message.length() > 0) {
            message.append(" ");
        }
    }

    private static boolean displayHours(int days, int hours) {
        return hours > 0 && days < 10;
    }

    private static boolean displayMinutes(int days, int hours, int minutes) {
        return minutes > 0 && hours < 10 && days == 0;
    }

    private CodeQualityMetricStatus metricStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return CodeQualityMetricStatus.Ok;
        }

        switch(status) {
            case STATUS_WARN:  return CodeQualityMetricStatus.Warning;
            case STATUS_ALERT: return CodeQualityMetricStatus.Alert;
            default:           return CodeQualityMetricStatus.Ok;
        }
    }

    private Long getTotalCount(JSONObject pagingObject) {
        return (Long) pagingObject.get("total");
    }

    private HttpHeaders createHeaders(String username, String password){
        HttpHeaders headers = new HttpHeaders();
        if (username != null && !username.isEmpty()) {
            String auth = username + ":" + (password == null ? "" : password);
            byte[] encodedAuth = Base64.encodeBase64(
                    auth.getBytes(Charset.forName("US-ASCII"))
            );
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set("Authorization", authHeader);
        }
        return headers;
    }

    private HttpHeaders createHeaders(String token) {
        String auth = token.trim();
        auth = auth+":";
        byte[] encodedAuth = Base64.encodeBase64(
                auth.getBytes(Charset.forName("US-ASCII"))
        );
        String authHeader = "Basic " + new String(encodedAuth);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return headers;
    }

    protected HttpHeaders setHeaders(RestUserInfo userInfo){
        if(Objects.isNull(userInfo)) return null;
        if(StringUtils.isNotBlank(userInfo.getUserId())){
            return createHeaders(userInfo.getUserId(),userInfo.getPassCode());
        }else if(StringUtils.isNotBlank(userInfo.getToken())){
            return createHeaders(userInfo.getToken());
        }
        return null;
    }
}
