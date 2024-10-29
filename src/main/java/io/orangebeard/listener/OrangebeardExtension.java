package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.entity.FinishV3TestRun;
import io.orangebeard.client.entity.LogFormat;
import io.orangebeard.client.entity.StartV3TestRun;

import io.orangebeard.client.entity.log.Log;
import io.orangebeard.client.entity.log.LogLevel;
import io.orangebeard.client.entity.suite.StartSuite;
import io.orangebeard.client.entity.test.FinishTest;
import io.orangebeard.client.entity.test.StartTest;
import io.orangebeard.client.entity.test.TestStatus;
import io.orangebeard.client.entity.test.TestType;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public class OrangebeardExtension implements
        Extension,
        BeforeAllCallback,
        BeforeEachCallback,
        AfterTestExecutionCallback,
        AfterEachCallback,
        AfterAllCallback,
        TestWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrangebeardExtension.class);

    private final OrangebeardContext runContext = OrangebeardContext.getInstance();
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
        if (runContext.getClient() == null) {
            runContext.setClient(new OrangebeardAsyncV3Client(orangebeardProperties));
        }
        startTestRunAndAddShutdownHook(orangebeardProperties);
        this.testrunUUID = runContext.getTestRunUUID();
    }

    OrangebeardExtension(OrangebeardAsyncV3Client orangebeardClient) {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        runContext.setClient(orangebeardClient);
        startTestRunAndAddShutdownHook(orangebeardProperties);
        this.testrunUUID = runContext.getTestRunUUID();
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

                // Get the UUID of the test suite in the parent node; use "null" if the parent node is the root node.
                // The node key is usually the String representation of that UUID, but that is not guaranteed.
                // This is why we keep track of a node's test suite UUID separately.
                UUID parentSuiteUuid = null;
                if (parentNode != root) {
                    parentSuiteUuid = parentNode.getTestSuiteUUID();
                }

                StartSuite startSuite = new StartSuite(testrunUUID, parentSuiteUuid, null, null, List.of(classNameComponents[i]));
                UUID suiteId = runContext.getClient().startSuite(startSuite).get(0);

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
        //no longer needed in V3 as suites have no status
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (extensionContext.getParent().isPresent()) {
            String parentId = getParentId(extensionContext.getParent().get());
            Optional<TestSuiteTree> node = root.findSubtree(parentId);
            if (node.isPresent()) {
                UUID suiteId = node.get().getTestSuiteUUID();
                StartTest startTest = new StartTest(testrunUUID, suiteId, extensionContext.getDisplayName(), TestType.TEST, getCodeRef(extensionContext), null, ZonedDateTime.now());
                UUID testId = runContext.getClient().startTest(startTest);
                runContext.addTest(extensionContext.getUniqueId(), testId);
            }
        } else {
            LOGGER.warn("Test with the name [{}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        //Optional after execution actions
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) {
        //Optional after execution actions
    }


    @Override
    public void testDisabled(ExtensionContext extensionContext, Optional<String> reason) {
        if (extensionContext.getParent().isPresent()) {
            String parentId = getParentId(extensionContext);
            Optional<TestSuiteTree> node = root.findSubtree(parentId);
            if (node.isPresent()) {
                UUID suiteId = node.get().getTestSuiteUUID();

                StartTest startTest = new StartTest(testrunUUID, suiteId, extensionContext.getDisplayName(), TestType.TEST, getCodeRef(extensionContext), null, ZonedDateTime.now());
                UUID testId = runContext.getClient().startTest(startTest);

                FinishTest finishTest = new FinishTest(testrunUUID, TestStatus.SKIPPED, ZonedDateTime.now());
                reason.ifPresent(s -> runContext.getClient().log(new Log(testrunUUID, testId, null, s, LogLevel.WARN, ZonedDateTime.now(), LogFormat.PLAIN_TEXT)));
                runContext.getClient().finishTest(testId, finishTest);
            }
        } else {
            LOGGER.warn("Test with the name [{}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
        }
    }

    @Override
    public void testSuccessful(ExtensionContext extensionContext) {
        reportTestResult(extensionContext, TestStatus.PASSED);
    }

    @Override
    public void testAborted(ExtensionContext extensionContext, Throwable cause) {
        reportTestResult(extensionContext, TestStatus.STOPPED);
    }

    @Override
    public void testFailed(ExtensionContext extensionContext, Throwable cause) {
        UUID testId = runContext.getTestId(extensionContext.getUniqueId());
        if (testId == null) {
            // probably, a test failed before it was properly started (initialization issue). Start the test and fail it immediately.
            beforeEach(extensionContext);
            testId = runContext.getTestId(extensionContext.getUniqueId());
        }

        FinishTest finishTest = new FinishTest(testrunUUID, TestStatus.FAILED, ZonedDateTime.now());

        runContext.getClient().log(new Log(testrunUUID, testId, null,  cause.getMessage(), LogLevel.ERROR, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
        runContext.getClient().log(new Log(testrunUUID, testId, null, ExceptionUtils.readStackTrace(cause), LogLevel.INFO, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));

        runContext.getClient().finishTest(testId, finishTest);
    }

    private void reportTestResult(ExtensionContext extensionContext, TestStatus status) {
        UUID testId = runContext.getTestId(extensionContext.getUniqueId());
        FinishTest finishTest = new FinishTest(testrunUUID, status, ZonedDateTime.now());

        if (extensionContext.getExecutionException().isPresent()) {
            Throwable cause = extensionContext.getExecutionException().get();

            runContext.getClient().log(new Log(testrunUUID, testId, null,  cause.getMessage(), LogLevel.WARN, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
            runContext.getClient().log(new Log(testrunUUID, testId, null, ExceptionUtils.readStackTrace(cause), LogLevel.WARN, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
        }
        runContext.getClient().finishTest(testId, finishTest);
    }

    private String getCodeRef(ExtensionContext extensionContext) {
        if (extensionContext.getTestClass().isPresent()) {
            return extensionContext.getTestClass().get().getName() + "." + extensionContext.getRequiredTestMethod().getName();
        } else {
            return extensionContext.getRequiredTestMethod().getName();
        }
    }

    private void startTestRunAndAddShutdownHook(OrangebeardProperties orangebeardProperties) {
        StartV3TestRun testRun = new StartV3TestRun(orangebeardProperties.getTestSetName(), orangebeardProperties.getDescription(), orangebeardProperties.getAttributes());
        this.testrunUUID = runContext.getClient().startTestRun(testRun);
        runContext.setTestRunUUID(testrunUUID);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            // Then finish the test run itself.
            FinishV3TestRun finishTestRun = new FinishV3TestRun();
            runContext.getClient().finishTestRun(testrunUUID, finishTestRun);
        }));
    }

    private static String getParentId(ExtensionContext parentContext) {
        String parentId = parentContext.getUniqueId();
        if (parentId.contains("[test-template:")) {
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
