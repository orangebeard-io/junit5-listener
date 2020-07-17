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
        readPropertyFile("orangebeard.properties");
        readEnvironmentVariables(".");
        readEnvironmentVariables("_");
    }

    public boolean requiredValuesArePresent() {
        return endpoint != null && accessToken != null && projectName != null && testSetName != null;
    }

    private void readPropertyFile(String name) {
        try {
            Properties properties = new Properties();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(name);

            if (inputStream != null) {
                properties.load(inputStream);
                this.propertyFilePresent = true;
            }
            this.endpoint = properties.getProperty("orangebeard.endpoint");
            try {
                this.accessToken = properties.getProperty("orangebeard.accessToken") != null ? UUID.fromString(properties.getProperty("orangebeard.accessToken")) : null;
            } catch (IllegalArgumentException e) {
                LOGGER.warn("orangebeard.accessToken is not a valid UUID!");
            }
            this.projectName = properties.getProperty("orangebeard.project");
            this.testSetName = properties.getProperty("orangebeard.testset");
            this.description = properties.getProperty("orangebeard.description");
            this.attributes = extractAttributes(properties.getProperty("orangebeard.attributes"));
        } catch (IOException e) {
            this.propertyFilePresent = false;
        }
    }

    private void readEnvironmentVariables(String separator) {
        if (System.getenv("orangebeard" + separator + "endpoint") != null) {
            this.endpoint = System.getenv("orangebeard" + separator + "endpoint");
        }
        if (System.getenv("orangebeard.accessToken") != null) {
            try {
                this.accessToken = UUID.fromString(System.getenv("orangebeard" + separator + "accessToken"));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("orangebeard" + separator + "accessToken is not a valid UUID!");
            }
        }
        if (System.getenv("orangebeard" + separator + "project") != null) {
            this.projectName = System.getenv("orangebeard" + separator + "project");
        }
        if (System.getenv("orangebeard" + separator + "testset") != null) {
            this.testSetName = System.getenv("orangebeard" + separator + "testset");
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
