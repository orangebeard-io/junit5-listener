package io.orangebeard.listener;

import io.orangebeard.listener.entity.FinishTestItem;
import io.orangebeard.listener.entity.FinishTestRun;
import io.orangebeard.listener.entity.Log;
import io.orangebeard.listener.entity.Response;
import io.orangebeard.listener.entity.StartTestItem;
import io.orangebeard.listener.entity.TestRun;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import static java.lang.String.format;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

public class OrangebeardClient {
    private final String endpoint;
    private final RestTemplate restTemplate;
    private final UUID uuid;
    private final String projectName;
    private boolean connectionWithOrangebeardIsValid;
    private static final Logger LOGGER = LoggerFactory.getLogger(OrangebeardClient.class);

    public OrangebeardClient(String endpoint, UUID uuid, String projectName, boolean connectionWithOrangebeardIsValid) {
        this.restTemplate = new RestTemplate();
        this.endpoint = endpoint;
        this.uuid = uuid;
        this.projectName = projectName;
        this.connectionWithOrangebeardIsValid = connectionWithOrangebeardIsValid;
    }

    public UUID startTestRun(TestRun testRun) {
        if (connectionWithOrangebeardIsValid) {
            try {
                HttpEntity<TestRun> request = new HttpEntity<>(testRun, getAuthorizationHeaders(uuid.toString()));
                return restTemplate.exchange(format("%s/api/v1/%s/launch", endpoint, projectName), POST, request, Response.class).getBody().getId();
            } catch (Exception e) {
                LOGGER.error("The connection with Orangebeard could not be established! Check the properties and try again!");
                connectionWithOrangebeardIsValid = false;
            }
        }
        return null;
    }

    public UUID startSuite(StartTestItem testItem) {
        if (connectionWithOrangebeardIsValid) {
            HttpEntity<StartTestItem> request = new HttpEntity<>(testItem, getAuthorizationHeaders(uuid.toString()));
            return restTemplate.exchange(format("%s/api/v1/%s/item", endpoint, projectName), POST, request, Response.class).getBody().getId();
        }
        return null;
    }

    public UUID startTest(UUID suiteId, StartTestItem testItem) {
        if (connectionWithOrangebeardIsValid) {

            HttpEntity<StartTestItem> request = new HttpEntity<>(testItem, getAuthorizationHeaders(uuid.toString()));
            return restTemplate.exchange(format("%s/api/v1/%s/item/%s", endpoint, projectName, suiteId), POST, request, Response.class).getBody().getId();
        } else {
            LOGGER.warn("The connection with Orangebeard could not be established!");
        }
        return null;
    }

    public void finishTestItem(UUID itemId, FinishTestItem finishTestItem) {
        if (connectionWithOrangebeardIsValid) {
            HttpEntity<FinishTestItem> request = new HttpEntity<>(finishTestItem, getAuthorizationHeaders(uuid.toString()));
            restTemplate.exchange(format("%s/api/v1/%s/item/%s", endpoint, projectName, itemId), PUT, request, Response.class);
        } else {
            LOGGER.warn("The connection with Orangebeard could not be established!");
        }
    }

    public void finishTestRun(UUID testRunUUID, FinishTestRun finishTestRun) {
        if (connectionWithOrangebeardIsValid) {
            HttpEntity<FinishTestRun> request = new HttpEntity<>(finishTestRun, getAuthorizationHeaders(uuid.toString()));
            restTemplate.exchange(format("%s/api/v1/%s/launch/%s/finish", endpoint, projectName, testRunUUID), PUT, request, Response.class);
        } else {
            LOGGER.warn("The connection with Orangebeard could not be established!");
        }
    }

    public void log(Log log) {
        if (connectionWithOrangebeardIsValid) {
            HttpEntity<Log> request = new HttpEntity<>(log, getAuthorizationHeaders(uuid.toString()));
            restTemplate.exchange(format("%s/api/v1/%s/log", endpoint, projectName), POST, request, Response.class);
        } else {
            LOGGER.warn("The connection with Orangebeard could not be established!");
        }
    }

    private HttpHeaders getAuthorizationHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((accessToken));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
