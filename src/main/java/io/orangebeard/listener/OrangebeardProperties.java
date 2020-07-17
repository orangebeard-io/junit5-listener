package io.orangebeard.listener;

import io.orangebeard.listener.entity.Attribute;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_ACCESS_TOKEN;
import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_ATTRIBUTES;
import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_DESCRIPTION;
import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_ENDPOINT;
import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_PROJECT;
import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_PROPERTY_FILE;
import static io.orangebeard.listener.PropertyNames.ORANGEBEARD_TESTSET;
import static java.lang.System.getenv;

@Getter
public class OrangebeardProperties {
    private String endpoint;
    private UUID accessToken;
    private String projectName;
    private String testSetName;
    private String description;
    private Set<Attribute> attributes;
    private boolean propertyFilePresent;

    private static final Logger LOGGER = LoggerFactory.getLogger(OrangebeardProperties.class);

    OrangebeardProperties(String propertyFile) {
        readPropertyFile(propertyFile);
        readEnvironmentVariables(".");
        readEnvironmentVariables("_");
    }

    public OrangebeardProperties() {
        readPropertyFile(ORANGEBEARD_PROPERTY_FILE);
        readEnvironmentVariables(".");
        readEnvironmentVariables("_");
    }

    public boolean requiredValuesArePresent() {
        return endpoint != null && accessToken != null && projectName != null && testSetName != null;
    }

    public void checkPropertiesArePresent() {
        if (!requiredValuesArePresent() && !propertyFilePresent) {
            LOGGER.error("Required Orangebeard properties are missing. Not all environment variables are present, and orangebeard.properties cannot be found!\n" + requiredPropertiesToString());
        }
        if (!requiredValuesArePresent()) {
            LOGGER.error("Required Orangebeard properties are missing. Not all environment variables are present, and/or orangebeard.properties misses required values!\n" + requiredPropertiesToString());
        }
    }

    private String requiredPropertiesToString() {
        return ORANGEBEARD_ENDPOINT + ": " + endpoint + "\n" +
                ORANGEBEARD_ACCESS_TOKEN + ": " + (accessToken != null ? "HIDDEN (PRESENT)" : "null\n") +
                ORANGEBEARD_PROJECT + ": " + projectName + "\n" +
                ORANGEBEARD_TESTSET + ": " + testSetName + "\n";
    }

    private void readPropertyFile(String name) {
        try {
            Properties properties = new Properties();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(name);

            if (inputStream != null) {
                properties.load(inputStream);
                this.propertyFilePresent = true;
            }
            this.endpoint = properties.getProperty(ORANGEBEARD_ENDPOINT);
            try {
                this.accessToken = properties.getProperty(ORANGEBEARD_ACCESS_TOKEN) != null ? UUID.fromString(properties.getProperty(ORANGEBEARD_ACCESS_TOKEN)) : null;
            } catch (IllegalArgumentException e) {
                LOGGER.warn(ORANGEBEARD_ACCESS_TOKEN+" is not a valid UUID!");
            }
            this.projectName = properties.getProperty(ORANGEBEARD_PROJECT);
            this.testSetName = properties.getProperty(ORANGEBEARD_TESTSET);
            this.description = properties.getProperty(ORANGEBEARD_DESCRIPTION);
            this.attributes = extractAttributes(properties.getProperty(ORANGEBEARD_ATTRIBUTES));
        } catch (IOException e) {
            this.propertyFilePresent = false;
        }
    }

    private void readEnvironmentVariables(String separator) {
        if (getenv(ORANGEBEARD_ENDPOINT.replace(".", separator)) != null) {
            this.endpoint = getenv(ORANGEBEARD_ENDPOINT.replace(".", separator));
        }
        if (getenv(ORANGEBEARD_ACCESS_TOKEN.replace(".", separator)) != null) {
            try {
                this.accessToken = UUID.fromString(getenv(ORANGEBEARD_ACCESS_TOKEN.replace(".", separator)));
            } catch (IllegalArgumentException e) {
                LOGGER.warn(ORANGEBEARD_ACCESS_TOKEN.replace(".", separator) + " is not a valid UUID!");
            }
        }
        if (getenv(ORANGEBEARD_PROJECT.replace(".", separator)) != null) {
            this.projectName = getenv(ORANGEBEARD_PROJECT.replace(".", separator));
        }
        if (getenv(ORANGEBEARD_TESTSET.replace(".", separator)) != null) {
            this.testSetName = getenv(ORANGEBEARD_TESTSET.replace(".", separator));
        }
    }

    private Set<Attribute> extractAttributes(String attributeString) {
        Set<Attribute> attributes = new HashSet<>();

        if (attributeString == null) {
            return attributes;
        }

        for (String attribute : attributeString.split(";")) {
            if (attribute.contains(":")) {
                String[] keyValue = attribute.split(":");
                attributes.add(new Attribute(keyValue[0].trim(), keyValue[1].trim()));
            } else {
                attributes.add(new Attribute(attribute.trim()));
            }
        }
        return attributes;
    }
}
