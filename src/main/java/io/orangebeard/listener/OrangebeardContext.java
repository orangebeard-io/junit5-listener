package io.orangebeard.listener;

import io.orangebeard.client.entity.FinishV3TestRun;
import io.orangebeard.client.entity.StartV3TestRun;
import io.orangebeard.client.v3.V3Client;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * State shared by everything in the JVM that reports to Orangebeard: the client, the one test run and the
 * ids of the tests that are being reported. The extension and the execution listener are created independently
 * by JUnit, so this is where they find each other.
 */
public class OrangebeardContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrangebeardContext.class);

    @Getter
    private static final OrangebeardContext instance = new OrangebeardContext();

    private final Map<String, UUID> tests = new ConcurrentHashMap<>();

    private volatile V3Client client = null;
    private volatile UUID testRunUUID = null;
    /** The suites started for the current run. Shared, because every test class may get its own extension instance. */
    private volatile TestSuiteTree suiteTree = newSuiteTree();
    /** Finishes the run when the JVM exits without the end of the test plan having been seen. Guarded by this. */
    private Thread shutdownHook;

    private static TestSuiteTree newSuiteTree() {
        UUID rootUUID = UUID.fromString("342e7cc4-8ac6-4d2a-8659-10bee9060de0");
        return new TestSuiteTree("ROOT", rootUUID.toString(), rootUUID);
    }

    private OrangebeardContext() {
        //prevent instantiation
    }

    public V3Client getClient() {
        return client;
    }

    public void setClient(V3Client client) {
        this.client = client;
    }

    public UUID getTestRunUUID() {
        return testRunUUID;
    }

    public void setTestRunUUID(UUID testRunUUID) {
        this.testRunUUID = testRunUUID;
    }

    TestSuiteTree suiteTree() {
        return suiteTree;
    }

    public void addTest(String identifier, UUID testId) {
        if (identifier != null && testId != null) {
            tests.put(identifier, testId);
        }
    }

    public UUID getTestId(String identifier) {
        return identifier == null ? null : tests.get(identifier);
    }

    void removeTest(String identifier) {
        if (identifier != null) {
            tests.remove(identifier);
        }
    }

    /** Returns the client, creating it first if there is none yet. */
    synchronized V3Client ensureClient(Supplier<? extends V3Client> factory) {
        if (client == null) {
            client = factory.get();
        }
        return client;
    }

    /**
     * Returns the id of the current test run, starting it first if there is none. The run is finished by
     * {@link #finishRun()}, or by a shutdown hook if that is never called.
     */
    synchronized UUID ensureRunStarted(StartV3TestRun testRun) {
        if (testRunUUID == null) {
            testRunUUID = client.startTestRun(testRun);
            shutdownHook = new Thread(this::finishRun, "orangebeard-finish-test-run");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
        return testRunUUID;
    }

    /** Finishes the current test run, if there is one. Safe to call more than once. */
    synchronized void finishRun() {
        UUID runToFinish = testRunUUID;
        if (runToFinish == null) {
            return;
        }
        testRunUUID = null;
        tests.clear();
        suiteTree = newSuiteTree();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // already shutting down: this is the shutdown hook
        }
        shutdownHook = null;
        try {
            client.finishTestRun(runToFinish, new FinishV3TestRun());
        } catch (RuntimeException e) {
            LOGGER.warn("Could not finish the Orangebeard test run: {}", e.getMessage());
        }
    }

    /** Forgets everything. For tests. */
    synchronized void reset() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // shutting down
            }
        }
        shutdownHook = null;
        client = null;
        testRunUUID = null;
        tests.clear();
        suiteTree = newSuiteTree();
    }
}
