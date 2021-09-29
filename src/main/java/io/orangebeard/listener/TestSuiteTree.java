package io.orangebeard.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** Construct a new Tree of test suites.
     * @param name Name of the root node.
     * @param uuid Name of the test suite's UUID.
     */
    TestSuiteTree(@NonNull String name, @Nullable UUID uuid) {
        this.testSuiteName = name;
        this.testSuiteUuid = uuid;
    }

    String getName() { return testSuiteName; }
    UUID getTestSuiteUuid() {
        return testSuiteUuid;
    }

    @Override
    public String toString() {
        return String.format("(%s,%s)", testSuiteUuid, testSuiteName);
    }

    /**
     * Add a child node to the given tree node.
     * The name of the child node must be unique among its siblings.
     * In other words, a node should not have two or more children with the same value for "name".
     * @param name Name of the new child node.
     * @param testSuiteUuid UUID of the test suite.
     * @return An Optional containing the newly added node, or an empty Optional if there already was a child node with the given name.
     */
    TestSuiteTree addChild(@NonNull String name, UUID testSuiteUuid) {
        // The field "name" should be unique among the children of a node.
        if (getChildByName(name).isPresent()) {
            return null;
        }
        // If there is not already a child node with the given name, create and add it.
        TestSuiteTree child = new TestSuiteTree(name, testSuiteUuid);
        children.add(child);
        child.parent = this;
        return child;
    }

    TestSuiteTree findSubtree(@NonNull UUID uuid) {
        if (uuid.equals(testSuiteUuid)) {
            return this;
        }

        for (TestSuiteTree child : children) {
            TestSuiteTree searchResult = child.findSubtree(uuid);
            if (searchResult != null) {
                return searchResult;
            }
        }
        return null;

    }

    /**
     * Finds the direct child node with the given name; or <code>null</code> if there is no such node.
     * Names of child nodes should be unique among their siblings.
     * @param name Name of the requested child node.
     * @return An Optional containing the child node with the given name; or an empty Optional if there is no such node.
     */
    Optional<TestSuiteTree> getChildByName(@NonNull String name) {
        return children
                .stream()
                .filter(x->name.compareTo(x.getName())==0)
                .findFirst()
                ;
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

        LOGGER.info(logEntry);
        //System.out.println(logEntry);

        for (TestSuiteTree child : children) {
            child.log(indent+2);
        }
    }

}
