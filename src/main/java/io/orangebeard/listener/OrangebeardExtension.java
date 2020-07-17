package io.orangebeard.listener;

import io.orangebeard.listener.entity.FinishTestItem;
import io.orangebeard.listener.entity.FinishTestRun;
import io.orangebeard.listener.entity.Log;
import io.orangebeard.listener.entity.StartTestItem;
import io.orangebeard.listener.entity.StartTestRun;
import io.orangebeard.listener.entity.Status;
import io.orangebeard.listener.entity.Suite;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.orangebeard.listener.entity.LogLevel.error;
import static io.orangebeard.listener.entity.LogLevel.info;
import static io.orangebeard.listener.entity.LogLevel.warn;
import static io.orangebeard.listener.entity.Status.FAILED;
import static io.orangebeard.listener.entity.Status.PASSED;
import static io.orangebeard.listener.entity.Status.SKIPPED;
import static io.orangebeard.listener.entity.TestItemType.STEP;
import static io.orangebeard.listener.entity.TestItemType.SUITE;

public class OrangebeardExtension implements
        Extension,
        BeforeAllCallback,
        BeforeEachCallback,
        AfterTestExecutionCallback,
        AfterEachCallback,
        AfterAllCallback,
        TestWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrangebeardExtension.class);

    private final OrangebeardClient orangebeardClient;
    private UUID testrunUUID;
    private Status testrunStatus = PASSED;
    private final Map<String, Suite> suites = new HashMap<>();
    private final Map<String, UUID> runningTests = new HashMap<>();

    public OrangebeardExtension() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        if (!orangebeardProperties.requiredValuesArePresent() && !orangebeardProperties.isPropertyFilePresent()) {
            LOGGER.error("Required Orangebeard properties are missing. Not all environment variables are present, and orangebeard.properties cannot be found!");
        }
        if (!orangebeardProperties.requiredValuesArePresent()) {
            LOGGER.error("Required Orangebeard properties are missing. Not all environment variables are present, and/or orangebeard.properties misses required values!");
        }

        this.orangebeardClient = new OrangebeardClient(
                orangebeardProperties.getEndpoint(),
                orangebeardProperties.getAccessToken(),
                orangebeardProperties.getProjectName(),
                orangebeardProperties.requiredValuesArePresent());

        startTestRunAndAddShutdownHook(orangebeardProperties);
    }

    OrangebeardExtension(OrangebeardClient orangebeardClient) {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        this.orangebeardClient = orangebeardClient;
        startTestRunAndAddShutdownHook(orangebeardProperties);
    }

    private void startTestRunAndAddShutdownHook(OrangebeardProperties orangebeardProperties) {
        StartTestRun testRun = new StartTestRun(orangebeardProperties.getTestSetName(), orangebeardProperties.getDescription(), orangebeardProperties.getAttributes());
        this.testrunUUID = orangebeardClient.startTestRun(testRun);

        Runtime.getRuntime().addShutdownHook(getShutdownHook());
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        StartTestItem testSuite = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), SUITE, null, null);
        UUID suiteId = orangebeardClient.startSuite(testSuite);
        suites.put(extensionContext.getUniqueId(), new Suite(suiteId));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        UUID suiteId = suites.get(extensionContext.getUniqueId()).getUuid();

        FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, FAILED, null, null);
        orangebeardClient.finishTestItem(suiteId, finishTestItem);
        suites.remove(extensionContext.getUniqueId());
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (extensionContext.getParent().isPresent()) {
            UUID suiteId = suites.get(extensionContext.getParent().get().getUniqueId()).getUuid();
            StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
            UUID testId = orangebeardClient.startTest(suiteId, test);
            runningTests.put(extensionContext.getUniqueId(), testId);
        } else {
            LOGGER.warn("Test with the name [{}}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) {

    }

    @Override
    public void testDisabled(ExtensionContext extensionContext, Optional<String> reason) {
        this.testrunStatus = FAILED;
        if (extensionContext.getParent().isPresent()) {
            UUID suiteId = suites.get(extensionContext.getParent().get().getUniqueId()).getUuid();
            StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
            UUID testId = orangebeardClient.startTest(suiteId, test);
            FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, SKIPPED, null, null);
            reason.ifPresent(s -> orangebeardClient.log(new Log(testrunUUID, testId, warn, s)));
            orangebeardClient.finishTestItem(testId, finishTestItem);
        } else {
            LOGGER.warn("Test with the name [{}}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
        }
    }

    @Override
    public void testSuccessful(ExtensionContext extensionContext) {
        reportTestResult(extensionContext, PASSED);
    }

    @Override
    public void testAborted(ExtensionContext extensionContext, Throwable cause) {
        this.testrunStatus = FAILED;
        reportTestResult(extensionContext, FAILED);
    }

    @Override
    public void testFailed(ExtensionContext extensionContext, Throwable cause) {
        this.testrunStatus = FAILED;
        UUID testId = runningTests.get(extensionContext.getUniqueId());
        suites.get(extensionContext.getParent().get().getUniqueId()).setStatus(FAILED);
        FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, FAILED, null, null);
        orangebeardClient.log(new Log(testrunUUID, testId, error, cause.getMessage()));
        orangebeardClient.log(new Log(testrunUUID, testId, info, ExceptionUtils.getStackTrace(cause)));

        orangebeardClient.finishTestItem(testId, finishTestItem);
    }

    private void reportTestResult(ExtensionContext extensionContext, Status status) {
        UUID testId = runningTests.get(extensionContext.getUniqueId());
        FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, status, null, null);
        orangebeardClient.finishTestItem(testId, finishTestItem);

        if (extensionContext.getExecutionException().isPresent()) {
            Throwable throwable = extensionContext.getExecutionException().get();
            orangebeardClient.log(new Log(testrunUUID, testId, warn, throwable.getMessage()));
            orangebeardClient.log(new Log(testrunUUID, testId, warn, ExceptionUtils.getStackTrace(throwable)));
        }
    }

    private String getCodeRef(ExtensionContext extensionContext) {
        if (extensionContext.getTestClass().isPresent()) {
            return extensionContext.getTestClass().get().getName() + "." + extensionContext.getRequiredTestMethod().getName();
        } else {
            return extensionContext.getRequiredTestMethod().getName();
        }
    }

    private Thread getShutdownHook() {
        return new Thread(() -> {
            FinishTestRun finishTestRun = new FinishTestRun(testrunStatus);
            orangebeardClient.finishTestRun(testrunUUID, finishTestRun);
        });
    }
}
