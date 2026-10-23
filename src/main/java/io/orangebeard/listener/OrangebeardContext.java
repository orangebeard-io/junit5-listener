package io.orangebeard.listener;

import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import lombok.Getter;
import lombok.Setter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class OrangebeardContext {
    @Getter
    private static final OrangebeardContext instance = new OrangebeardContext();

    private final Map<String, UUID> tests = new ConcurrentHashMap<>();

    @Getter @Setter
    private OrangebeardAsyncV3Client client = null;

    @Getter @Setter
    private UUID testRunUUID = null;

    private OrangebeardContext() {
        //prevent instantiation
    }

    public void addTest(String identifier, UUID testId) {
        if (identifier != null && testId != null) {
            instance.tests.put(identifier, testId);
        }
    }

    public UUID getTestId(String identifier) {
        return identifier == null ? null : instance.tests.get(identifier);
    }
}
