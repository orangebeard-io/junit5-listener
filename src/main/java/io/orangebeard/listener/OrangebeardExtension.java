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
import java.util.List;
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
    private final Map<String, UUID> runningTests = new HashMap<>();
    private UUID testrunUUID;

    /** Tree-structure to keep track of the hierarchy of test suites.
     * Initialized with an arbitrary ID. Note that this same arbitrary value is, by necessity, used in the unit tests.
     */
    private final TestSuiteTree root = new TestSuiteTree("ROOT","342e7cc4-8ac6-4d2a-8659-10bee9060de0", null);

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
        Class<?> requiredTestClass = extensionContext.getRequiredTestClass();
        String[] classNameComponents = getFullyQualifiedClassName(requiredTestClass);

        // "classNameComponents" contains the fully qualified name of the class under test, split into sections.
        // We iterate over this array. For each element, we check if it is in the tree.
        // If the element is already in the tree, then a test suite was already started for this, and we don't have to do anything.
        // For every element NOT already in the tree, then we must start a new test suite, and add a node to the tree.
        // We must also store these newly started test suites in the "suites" map.
        TestSuiteTree parentNode = root;
        for (int i = 0; i < classNameComponents.length; i++) {
            Optional<TestSuiteTree> currentNode = parentNode.getChildByName(classNameComponents[i]);
            if (currentNode.isEmpty()) {
                // Create and start the test suite.
                StartTestItem startTestItem = new StartTestItem(testrunUUID, classNameComponents[i], SUITE, null, null);
                String idOfParent = parentNode.getTestSuiteId();
                UUID uuidOfParent = null;
                if (idOfParent != null) {
                    uuidOfParent = UUID.fromString(idOfParent); //TODO?~ What if the parent's ID ISN'T a UUID?
                }
                UUID suiteId = orangebeardClient.startTestItem(uuidOfParent, startTestItem);

                // Add the newly created suite to the map of suites.
                String key = suiteId.toString();
                if (i == classNameComponents.length - 1) {
                    key = extensionContext.getUniqueId();
                }
                Suite suite = new Suite(suiteId);   // Note that "Suite" just keeps track of UUID and status.

                // Add a node to the tree for this newly created and started test suite.
                currentNode = Optional.of(parentNode.addChild(classNameComponents[i], key, suite));
            }
            // Continue with the next level of the package hierarchy.
            // At this point, `currentNode` is always filled.
            parentNode = currentNode.get();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // The test suites that were started, must now be finished cleanly.
        // After finishing a test suite, it should be removed from the tree.
        // If we remove a "parent" suite before its "children" suites, we can't remove that node from the tree without removing the children.
        // So we remove them layer by layer: remove only leaf nodes, after that remove what have now become leaf nodes, and so on.
        // Note that other approaches are possible.
        while (!root.isLeaf()) {
            List<TestSuiteTree> leaves = root.getLeaves();
            for (TestSuiteTree leaf : leaves) {
                Suite value = leaf.getTestSuite();
                UUID suiteId = value.getUuid();

                FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, PASSED, null, null);
                orangebeardClient.finishTestItem(suiteId, finishTestItem);
                leaf.detach();
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (extensionContext.getParent().isPresent()) {
            String parentId = extensionContext.getParent().get().getUniqueId();
            Suite suite = root.findSubtree(parentId).getTestSuite();
            UUID suiteId = suite.getUuid();
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
            String parentId = extensionContext.getParent().get().getUniqueId();
            TestSuiteTree subtree = root.findSubtree(parentId);
            UUID suiteId = subtree.getTestSuite().getUuid();

            StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
            UUID testId = orangebeardClient.startTestItem(suiteId, test);

            FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, SKIPPED, null, null);
            reason.ifPresent(s -> orangebeardClient.log(new Log(testrunUUID, testId, warn, s)));

            orangebeardClient.finishTestItem(testId, finishTestItem);
        } else {
            LOGGER.warn("Test with the name [{}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
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

    /**
     * Given a class, determine it fully qualified name (canonical name), split into its subpackages.
     * For example, if the input is the Class for "io.orangebeard.test.TestClass", this method will return the array ["io", "orangebeard", "test", "TestClass"].
     * If the input is <code>null</code> (as can happen in a unit test), then it returns an array with 0 elements.
     * @param clazz The class to analyze.
     * @return The fully qualified name of the class, split into its components. If there is no class (if the input is <code>null</code>), then this method returns an array with 0 elements.
     */
    private static String[] getFullyQualifiedClassName(Class<?> clazz) {
        String[] fullyQualifiedName = new String[] {};
        if (clazz != null) {
            fullyQualifiedName = clazz
                    .getCanonicalName()
                    .split("\\.")
                    ;
        }
        return fullyQualifiedName;
    }
}
