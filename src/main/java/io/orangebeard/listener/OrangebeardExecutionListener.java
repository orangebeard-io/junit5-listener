package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.entity.LogFormat;
import io.orangebeard.client.entity.log.Log;
import io.orangebeard.client.entity.log.LogLevel;

import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrangebeardExecutionListener implements TestExecutionListener {
    private final OrangebeardContext runContext = OrangebeardContext.getInstance();

    public OrangebeardExecutionListener() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        orangebeardProperties.checkPropertiesArePresent();
        if (runContext.getClient() == null) {
            runContext.setClient(new OrangebeardAsyncV3Client(orangebeardProperties));
        }
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        UUID testRunId = runContext.getTestRunUUID();
        UUID testId = runContext.getTestId(testIdentifier.getUniqueId());
        String logMessage = entry.getKeyValuePairs().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue() + "\n").collect(Collectors.joining());
        if (testRunId != null && testId != null) {
            runContext.getClient().log(new Log(testRunId, testId, null, logMessage, LogLevel.INFO, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
        }
    }
}
