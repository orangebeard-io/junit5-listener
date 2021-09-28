package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardClient;
import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV1Client;
import io.orangebeard.client.entity.FinishTestItem;
import io.orangebeard.client.entity.FinishTestRun;
import io.orangebeard.client.entity.Log;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;
import io.orangebeard.client.entity.Status;
import io.orangebeard.client.entity.Suite;

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
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.orangebeard.client.entity.LogLevel.error;
import static io.orangebeard.client.entity.LogLevel.info;
import static io.orangebeard.client.entity.LogLevel.warn;
import static io.orangebeard.client.entity.Status.FAILED;
import static io.orangebeard.client.entity.Status.PASSED;
import static io.orangebeard.client.entity.Status.SKIPPED;
import static io.orangebeard.client.entity.Status.STOPPED;
import static io.orangebeard.client.entity.TestItemType.STEP;
import static io.orangebeard.client.entity.TestItemType.SUITE;

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
    private final Map<String, Suite> suites = new HashMap<>();
    private final Map<String, UUID> runningTests = new HashMap<>();
    private UUID testrunUUID;

    // Added by SB for keeping track which suites have already been added.
    private final TestSuiteTree testsuiteHierarchy = new TestSuiteTree("ROOT", null); // Only the root node should have a null UUID.
    private final boolean useOldCode = false; //TODO!- Switch to be used during development, to easily compare with old code.

    public OrangebeardExtension() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        orangebeardProperties.checkPropertiesArePresent();

        this.orangebeardClient = new OrangebeardV1Client(
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

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        LOGGER.info("Starting beforeAll("+extensionContext+")");

        LOGGER.info("extensionContext=="+extensionContext);
        LOGGER.info("extensionContext.getTestClass()=="+extensionContext.getTestClass());
        LOGGER.info("extensionContext.getRequiredTestClass()=="+extensionContext.getRequiredTestClass());
        //LOGGER.info("extensionContext.getRequiredTestClass().getCanonicalName()=="+extensionContext.getRequiredTestClass().getCanonicalName());
        //LOGGER.info("extensionContext.getTestClass().getCanonicalName()=="+extensionContext.getTestClass().get().getCanonicalName());

        if (useOldCode) {
            // Original code:
            StartTestItem testSuite = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), SUITE, null, null);
            UUID suiteId = orangebeardClient.startTestItem(null, testSuite);
            suites.put(extensionContext.getUniqueId(), new Suite(suiteId));
        }
        else {
            Class<?> requiredTestClass = extensionContext.getRequiredTestClass();

            //TODO!~ This is a patch to get the unit tests working... because Mockito won't let you mock a class.
            String canonicalName = "nl.orangebeard.test";
            if (requiredTestClass != null) {
                canonicalName = requiredTestClass.getCanonicalName();
            }


            String[] canonicalNameComponents = canonicalName.split("\\.");

            // Walk over the tree.
            // For every element NOT already in the tree, start a suite, and add the associated node.
            // Store these newly created nodes in the "suites" map.
            TestSuiteTree cur = testsuiteHierarchy;
            for (int i = 0; i < canonicalNameComponents.length; i++) {
                TestSuiteTree child = cur.getChildByName(canonicalNameComponents[i]);
                if (child == null) {
                    // Create the test suite.
                    StartTestItem startTestItem = new StartTestItem(testrunUUID, canonicalNameComponents[i], SUITE, null, null);
                    UUID suiteId = orangebeardClient.startTestItem(cur.getTestSuiteUuid(), startTestItem);

                    // Add the newly created suite to the map of suites.
                    String key = suiteId.toString();
                    if (i == canonicalNameComponents.length - 1) {
                        key = extensionContext.getUniqueId();
                    }
                    suites.put(key, new Suite(suiteId));

                    // Add the newly created suite to the tree.
                    child = cur.addChild(canonicalNameComponents[i], suiteId);

                    // ...and continue with the next level.
                    cur = child;
                }
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (useOldCode) {
            UUID suiteId = suites.get(extensionContext.getUniqueId()).getUuid();

            FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, PASSED, null, null);
            orangebeardClient.finishTestItem(suiteId, finishTestItem);
            suites.remove(extensionContext.getUniqueId());
        } else {

            LOGGER.info("afterAll(...): suites.size()=="+suites.size());
            Iterator<String> iterator = suites.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                Suite value = suites.get(key);
                UUID suiteId = value.getUuid();
                LOGGER.info("...("+key+", "+value+")");

                FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, PASSED, null, null);
                orangebeardClient.finishTestItem(suiteId, finishTestItem);
                iterator.remove();
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (extensionContext.getParent().isPresent()) {
            UUID suiteId = suites.get(extensionContext.getParent().get().getUniqueId()).getUuid();
            StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
            UUID testId = orangebeardClient.startTestItem(suiteId, test);
            runningTests.put(extensionContext.getUniqueId(), testId);
        } else {
            LOGGER.warn("Test with the name [{}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
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
        if (extensionContext.getParent().isPresent()) {
            UUID suiteId = suites.get(extensionContext.getParent().get().getUniqueId()).getUuid();
            StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
            UUID testId = orangebeardClient.startTestItem(suiteId, test);
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
        reportTestResult(extensionContext, STOPPED);
    }

    @Override
    public void testFailed(ExtensionContext extensionContext, Throwable cause) {
        UUID testId = runningTests.get(extensionContext.getUniqueId());
        if (testId == null) {
            // probably, a test failed before it was properly started (initialization issue). Start the test and fail it afterwards.
            beforeEach(extensionContext);
            testId = runningTests.get(extensionContext.getUniqueId());
        }

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

    private void startTestRunAndAddShutdownHook(OrangebeardProperties orangebeardProperties) {
        StartTestRun testRun = new StartTestRun(orangebeardProperties.getTestSetName(), orangebeardProperties.getDescription(), orangebeardProperties.getAttributes());
        this.testrunUUID = orangebeardClient.startTestRun(testRun);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            FinishTestRun finishTestRun = new FinishTestRun();
            orangebeardClient.finishTestRun(testrunUUID, finishTestRun);
        }));
    }
}
