package io.orangebeard.listener;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


class TestSuiteTree {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSuiteTree.class);

    /** Name of the node. Unique among siblings. */
    private final String testSuiteName;
    /** UUID of the test suite at this level, assuming there is one. */
    private final UUID testSuiteUuid;
    /** Reference to the parent node. */
    private TestSuiteTree parent;
    /** Child nodes. */
    private final List<TestSuiteTree> children = new ArrayList<>();

    TestSuiteTree(String name, UUID uuid) {
        this.testSuiteName = name;
        this.testSuiteUuid = uuid;
    }

    UUID getTestSuiteUuid() {
        return testSuiteUuid;
    }

    TestSuiteTree addChild(String name, UUID testSuiteUuid) {
        // The field "name" should be unique among the children of a node.
        if (getChildByName(name) != null) {
            return null;
        }
        // If there is not already a child node with the given name, create and add it.
        TestSuiteTree child = new TestSuiteTree(name, testSuiteUuid);
        children.add(child);
        child.parent = this;
        return child;
    }

    /**
     * Finds the direct child node with the given name; or <code>null</code> if there is no such node.
     * Names of child nodes should be unique among their siblings.
     * @param name Name of the requested child node.
     * @return The child node with the given name, or <code>null</code> if there is no such node.
     */
    TestSuiteTree getChildByName(String name) {
        for (TestSuiteTree child : children) {
            if (child.testSuiteName.compareTo(name) == 0) {
                return child;
            }
        }
        return null;
    }

    void log(int indent) {

        // Create a String of "indent" spaces.
        // If indent is 0 or less, String.format throws an exception; so we need to handle that case.
        String spaces = "";
        if (indent > 0) {
            spaces = String.format("%"+indent+"s", " ");
        }

        String nodeUuidStr = null;
        if (testSuiteUuid != null) {
            nodeUuidStr = testSuiteUuid.toString();
        }

        String parentUuidStr = null;
        if (parent != null && parent.testSuiteUuid != null) {
            parentUuidStr = parent.testSuiteUuid.toString();
        }

        String logEntry = String.format("%s%s (UUID=%s, parent UUID=%s) [%s]", spaces, testSuiteName, nodeUuidStr, parentUuidStr, this);

        //LOGGER.info(logEntry);
        System.out.println(logEntry);

        for (TestSuiteTree child : children) {
            child.log(indent+2);
        }
    }

}
