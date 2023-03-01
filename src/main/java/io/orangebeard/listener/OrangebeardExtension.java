package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardClient;
import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV2Client;
import io.orangebeard.client.entity.FinishTestItem;
import io.orangebeard.client.entity.FinishTestRun;
import io.orangebeard.client.entity.Log;
import io.orangebeard.client.entity.LogFormat;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;
import io.orangebeard.client.entity.Status;

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

    /**
     * Arbitrary UUID for the root suite.
     * This value is, by necessity, also used in the unit tests.
     */
    private final UUID rootUUID = UUID.fromString("342e7cc4-8ac6-4d2a-8659-10bee9060de0");

    /**
     * Tree-structure to keep track of the hierarchy of test suites.
     */
    private final TestSuiteTree root = new TestSuiteTree("ROOT", rootUUID.toString(), rootUUID);

    public OrangebeardExtension() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        orangebeardProperties.checkPropertiesArePresent();

        this.orangebeardClient = new OrangebeardV2Client(
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
        TestSuiteTree parentNode = root;
        for (int i = 0; i < classNameComponents.length; i++) {
            Optional<TestSuiteTree> currentNode = parentNode.getChildByName(classNameComponents[i]);
            if (currentNode.isEmpty()) {
                // Create and start the test suite.
                StartTestItem startTestItem = new StartTestItem(testrunUUID, classNameComponents[i], SUITE, null, null);

                // Get the UUID of the test suite in the parent node; use "null" if the parent node is the root node.
                // The node key is usually the String representation of that UUID, but that is not guaranteed.
                // This is why we keep track of a node's test suite UUID separately.
                UUID uuidOfParent = null;
                if (parentNode != root) {
                    uuidOfParent = parentNode.getTestSuiteUUID();
                }

                // Now that we now the UUID of the parent suite, we can start a new suite as its child.
                UUID suiteId = orangebeardClient.startTestItem(uuidOfParent, startTestItem);

                if (suiteId != null) {
                    // Add a node to the tree for this newly created and started test suite.
                    String key = suiteId.toString();
                    if (i == classNameComponents.length - 1) {
                        key = extensionContext.getUniqueId();
                    }
                    currentNode = parentNode.addChild(classNameComponents[i], key, suiteId);
                }
            }
            // Continue with the next level of the package hierarchy.
            if (currentNode.isPresent()) {
                parentNode = currentNode.get();
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        String id = extensionContext.getUniqueId();
        Optional<TestSuiteTree> node = root.findSubtree(id);
        if (node.isPresent()) {
            UUID suiteId = node.get().getTestSuiteUUID();
            FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, null, null, null);
            orangebeardClient.finishTestItem(suiteId, finishTestItem);
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (extensionContext.getParent().isPresent()) {
            String parentId = getParentId(extensionContext.getParent().get());
            Optional<TestSuiteTree> node = root.findSubtree(parentId);
            if (node.isPresent()) {
                UUID suiteId = node.get().getTestSuiteUUID();
                StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
                UUID testId = orangebeardClient.startTestItem(suiteId, test);
                runningTests.put(extensionContext.getUniqueId(), testId);
            }
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
            String parentId = getParentId(extensionContext);
            Optional<TestSuiteTree> node = root.findSubtree(parentId);
            if (node.isPresent()) {
                UUID suiteId = node.get().getTestSuiteUUID();

                StartTestItem test = new StartTestItem(testrunUUID, extensionContext.getDisplayName(), STEP, getCodeRef(extensionContext), null);
                UUID testId = orangebeardClient.startTestItem(suiteId, test);

                FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, SKIPPED, null, null);
                reason.ifPresent(s -> orangebeardClient.log(new Log(testrunUUID, testId, warn, s, LogFormat.PLAIN_TEXT)));
                orangebeardClient.finishTestItem(testId, finishTestItem);
            }
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
        orangebeardClient.log(new Log(testrunUUID, testId, error, cause.getMessage(), LogFormat.PLAIN_TEXT));
        orangebeardClient.log(new Log(testrunUUID, testId, info, ExceptionUtils.getStackTrace(cause), LogFormat.PLAIN_TEXT));

        orangebeardClient.finishTestItem(testId, finishTestItem);
    }

    private void reportTestResult(ExtensionContext extensionContext, Status status) {
        UUID testId = runningTests.get(extensionContext.getUniqueId());
        FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, status, null, null);
        orangebeardClient.finishTestItem(testId, finishTestItem);

        if (extensionContext.getExecutionException().isPresent()) {
            Throwable throwable = extensionContext.getExecutionException().get();
            orangebeardClient.log(new Log(testrunUUID, testId, warn, throwable.getMessage(), LogFormat.PLAIN_TEXT));
            orangebeardClient.log(new Log(testrunUUID, testId, warn, ExceptionUtils.getStackTrace(throwable), LogFormat.PLAIN_TEXT));
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
            // First, finish the remaining intermediate test suites.
            finishAllSuites();

            // Then finish the test run itself.
            FinishTestRun finishTestRun = new FinishTestRun();
            orangebeardClient.finishTestRun(testrunUUID, finishTestRun);
        }));
    }

    /**
     * Finish all test suites.
     */
    private void finishAllSuites() {
        // Finish all test suites, one level at a time.
        // This way we make sure that test suites are only finished after all their children are finished.
        while (root.hasChildren()) {
            List<TestSuiteTree> leaves = root.getLeaves();
            // If is possible that the root has 0 children; then the root itself is a leaf node.
            // But the root node does not contain tests itself.
            leaves.remove(root);
            for (TestSuiteTree leaf : leaves) {
                UUID suiteId = leaf.getTestSuiteUUID();
                FinishTestItem finishTestItem = new FinishTestItem(testrunUUID, null, null, null);
                orangebeardClient.finishTestItem(suiteId, finishTestItem);
                leaf.detach();
            }
        }
    }

    private static String getParentId(ExtensionContext parentContext) {
            String parentId = parentContext.getUniqueId();
            if(parentId.contains("[test-template:")) {
                parentId = parentId.substring(0, parentId.indexOf("[test-template:") - 1);
            }
            return parentId;
    }

    /**
     * Given a class, determine it fully qualified name (canonical name), split into its subpackages.
     * For example, if the input is the Class for "io.orangebeard.test.TestClass", this method will return the array ["io", "orangebeard", "test", "TestClass"].
     * If the input is <code>null</code> (as can happen in a unit test), then it returns an array with 0 elements.
     *
     * @param clazz The class to analyze.
     * @return The fully qualified name of the class, split into its components. If there is no class (if the input is <code>null</code>), then this method returns an array with 0 elements.
     */
    private static String[] getFullyQualifiedClassName(Class<?> clazz) {
        String[] fullyQualifiedName = new String[]{};
        if (clazz != null) {
            fullyQualifiedName = clazz
                    .getCanonicalName()
                    .split("\\.")
            ;
        }
        return fullyQualifiedName;
    }
}
