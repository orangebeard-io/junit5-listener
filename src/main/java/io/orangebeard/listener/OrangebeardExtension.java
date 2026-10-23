package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
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
import io.orangebeard.client.v3.V3Client;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reports JUnit 5 test results to Orangebeard. Test classes become (nested) suites named after their package
 * and class, test methods become tests, and failures are attached to the test as logs.
 */
public class OrangebeardExtension implements
        BeforeAllCallback,
        BeforeEachCallback,
        LifecycleMethodExecutionExceptionHandler,
        TestWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrangebeardExtension.class);
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(OrangebeardExtension.class);

    private final OrangebeardContext runContext = OrangebeardContext.getInstance();
    private final V3Client client;
    private final UUID testrunUUID;

    /** Tree-structure to keep track of the hierarchy of test suites; shared by all extension instances of the run. */
    private final TestSuiteTree root;

    public OrangebeardExtension() {
        this(null, new OrangebeardProperties());
    }

    /**
     * @param orangebeardClient the client to report to; when null, one is created from the properties
     */
    OrangebeardExtension(V3Client orangebeardClient, OrangebeardProperties orangebeardProperties) {
        this.client = runContext.ensureClient(() -> {
            if (orangebeardClient != null) {
                return orangebeardClient;
            }
            orangebeardProperties.checkPropertiesArePresent();
            return new OrangebeardAsyncV3Client(orangebeardProperties);
        });
        this.root = runContext.suiteTree();
        this.testrunUUID = runContext.ensureRunStarted(new StartV3TestRun(
                orangebeardProperties.getTestSetName(), orangebeardProperties.getDescription(), orangebeardProperties.getAttributes()));
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        finishRunWhenTheTestPlanEnds(extensionContext);

        String[] classNameComponents = getFullyQualifiedClassName(extensionContext.getRequiredTestClass());

        // "classNameComponents" contains the fully qualified name of the class under test, split into sections.
        // The part that is already in the tree has a started suite; the rest is started in a single request.
        // The tree is locked, so classes that are set up in parallel do not start the same suite twice.
        root.locked(() -> {
            TestSuiteTree parentNode = root;
            int known = 0;
            while (known < classNameComponents.length) {
                Optional<TestSuiteTree> child = parentNode.getChildByName(classNameComponents[known]);
                if (child.isEmpty()) {
                    break;
                }
                parentNode = child.get();
                known++;
            }
            if (known == classNameComponents.length) {
                return null;
            }

            List<String> missing = new ArrayList<>(Arrays.asList(classNameComponents).subList(known, classNameComponents.length));
            // The node key is usually the String representation of the suite UUID, but that is not guaranteed.
            // This is why we keep track of a node's test suite UUID separately. The suite of the class itself is
            // registered under the id of its extension context, because that is what its tests' parent id is.
            UUID parentSuiteUuid = parentNode == root ? null : parentNode.getTestSuiteUUID();
            List<UUID> suiteIds = client.startSuite(new StartSuite(testrunUUID, parentSuiteUuid, null, null, missing));
            if (suiteIds == null || suiteIds.size() != missing.size()) {
                LOGGER.warn("Could not start the test suites {}; its tests will not be reported", missing);
                return null;
            }
            for (int i = 0; i < missing.size(); i++) {
                UUID suiteId = suiteIds.get(i);
                String key = i == missing.size() - 1 ? extensionContext.getUniqueId() : suiteId.toString();
                parentNode = parentNode.addChild(missing.get(i), key, suiteId).orElse(parentNode);
            }
            return null;
        });
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        startTest(extensionContext);
    }

    @Override
    public void testDisabled(ExtensionContext extensionContext, Optional<String> reason) {
        UUID testId = startTest(extensionContext);
        if (testId == null) {
            return;
        }
        reason.ifPresent(s -> log(testId, s, LogLevel.WARN));
        finishTest(extensionContext, testId, TestStatus.SKIPPED);
    }

    @Override
    public void testSuccessful(ExtensionContext extensionContext) {
        reportTestResult(extensionContext, TestStatus.PASSED, null);
    }

    @Override
    public void testAborted(ExtensionContext extensionContext, Throwable cause) {
        reportTestResult(extensionContext, TestStatus.STOPPED, cause);
    }

    @Override
    public void testFailed(ExtensionContext extensionContext, Throwable cause) {
        reportTestResult(extensionContext, TestStatus.FAILED, cause);
    }

    /** A failing {@code @BeforeAll} method fails the whole class without any of its tests being reported. */
    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext extensionContext, Throwable throwable) throws Throwable {
        reportClassLevelFailure(extensionContext, TestType.BEFORE, "Class setup (@BeforeAll)", throwable);
        throw throwable;
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext extensionContext, Throwable throwable) throws Throwable {
        reportClassLevelFailure(extensionContext, TestType.AFTER, "Class teardown (@AfterAll)", throwable);
        throw throwable;
    }

    private void reportClassLevelFailure(ExtensionContext classContext, TestType type, String name, Throwable cause) {
        Optional<TestSuiteTree> node = root.findSubtree(classContext.getUniqueId());
        if (node.isEmpty()) {
            LOGGER.warn("{} of [{}] failed, but its test suite was not started so the failure could not be reported", name, classContext.getDisplayName());
            return;
        }
        UUID testId = client.startTest(new StartTest(testrunUUID, node.get().getTestSuiteUUID(), name, type, null, null, ZonedDateTime.now()));
        logFailure(testId, cause, LogLevel.ERROR);
        client.finishTest(testId, new FinishTest(testrunUUID, TestStatus.FAILED, ZonedDateTime.now()));
    }

    /**
     * Starts the test of the given method context in its class' suite.
     *
     * @return the (temporary) id of the started test, or null if it could not be started
     */
    private UUID startTest(ExtensionContext extensionContext) {
        Optional<ExtensionContext> parent = extensionContext.getParent();
        if (parent.isEmpty()) {
            LOGGER.warn("Test with the name [{}] has no parent and therefore could not be reported", extensionContext.getDisplayName());
            return null;
        }
        Optional<TestSuiteTree> node = root.findSubtree(getParentId(parent.get()));
        if (node.isEmpty()) {
            LOGGER.warn("Test with the name [{}] has no started test suite and therefore could not be reported", extensionContext.getDisplayName());
            return null;
        }
        StartTest startTest = new StartTest(testrunUUID, node.get().getTestSuiteUUID(), extensionContext.getDisplayName(),
                TestType.TEST, getCodeRef(extensionContext), null, ZonedDateTime.now());
        UUID testId = client.startTest(startTest);
        runContext.addTest(extensionContext.getUniqueId(), testId);
        return testId;
    }

    private void reportTestResult(ExtensionContext extensionContext, TestStatus status, Throwable cause) {
        UUID testId = runContext.getTestId(extensionContext.getUniqueId());
        if (testId == null) {
            // probably, a test failed before it was properly started (initialization issue). Start the test first.
            testId = startTest(extensionContext);
        }
        if (testId == null) {
            LOGGER.warn("The result [{}] of test [{}] could not be reported because the test was not started", status, extensionContext.getDisplayName());
            return;
        }
        if (cause != null) {
            logFailure(testId, cause, status == TestStatus.FAILED ? LogLevel.ERROR : LogLevel.WARN);
        }
        finishTest(extensionContext, testId, status);
    }

    private void finishTest(ExtensionContext extensionContext, UUID testId, TestStatus status) {
        client.finishTest(testId, new FinishTest(testrunUUID, status, ZonedDateTime.now()));
        runContext.removeTest(extensionContext.getUniqueId());
    }

    private void logFailure(UUID testId, Throwable cause, LogLevel level) {
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
        log(testId, message, level);
        log(testId, stackTrace(cause), level);
    }

    private void log(UUID testId, String message, LogLevel level) {
        client.log(new Log(testrunUUID, testId, null, message, level, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
    }

    /**
     * Finishes the test run when the root context closes, i.e. as soon as the test plan is done. The shutdown hook
     * registered with the run is only a fallback for when that does not happen.
     */
    private void finishRunWhenTheTestPlanEnds(ExtensionContext extensionContext) {
        ExtensionContext rootContext = extensionContext.getRoot();
        if (rootContext != null) {
            rootContext.getStore(NAMESPACE).getOrComputeIfAbsent(
                    FinishRunWhenClosed.class, key -> new FinishRunWhenClosed(runContext), FinishRunWhenClosed.class);
        }
    }

    private record FinishRunWhenClosed(OrangebeardContext context) implements AutoCloseable {
        @Override
        public void close() {
            context.finishRun();
        }
    }

    private String getCodeRef(ExtensionContext extensionContext) {
        String methodName = extensionContext.getTestMethod().map(Method::getName).orElse(extensionContext.getDisplayName());
        return extensionContext.getTestClass()
                .map(testClass -> testClass.getName().replace('$', '.') + "." + methodName)
                .orElse(methodName);
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
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
     * Nested, anonymous and local classes have no (usable) canonical name, so the binary name is used with '$' read as a separator.
     *
     * @param clazz The class to analyze.
     * @return The fully qualified name of the class, split into its components. If there is no class (if the input is <code>null</code>), then this method returns an array with 0 elements.
     */
    private static String[] getFullyQualifiedClassName(Class<?> clazz) {
        if (clazz == null) {
            return new String[]{};
        }
        return clazz.getName().replace('$', '.').split("\\.");
    }
}
