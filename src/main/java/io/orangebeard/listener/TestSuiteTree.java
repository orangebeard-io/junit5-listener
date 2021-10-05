package io.orangebeard.listener;

import io.orangebeard.client.entity.Suite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class TestSuiteTree {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSuiteTree.class);

    /** Name of the node. Unique among siblings. */
    private final String testSuiteName;

    /** Unique ID of the test suite at this level. */
    private final String testSuiteId;

    /** The test suite associated with this ID. */
    private final Suite testSuite;

    /** Reference to the parent node. */
    private TestSuiteTree parent;

    /** Child nodes. */
    private final List<TestSuiteTree> children = new ArrayList<>();

    /** Construct a new Tree of test suites.
     * @param name Name of the root node.
     * @param testSuiteId ID of the test suite. Often a UUID in String form, but not always.
     */
    public TestSuiteTree(@NonNull String name, @NonNull String testSuiteId, @Nullable Suite testSuite) {
        this.testSuiteName = name;
        this.testSuiteId = testSuiteId;
        this.testSuite = testSuite;
    }

    public String getName() { return testSuiteName; }
    public String getTestSuiteId() { return testSuiteId; }
    public Suite getTestSuite() { return testSuite; }

    @Override
    public String toString() {
        return String.format("(%s,%s,%s,%s)", testSuiteId, testSuiteName, testSuite, testSuite == null ? "No suite ID" : testSuite.getUuid());
    }

    /**
     * Add a child node to the given tree node.
     * The name of the child node must be unique among its siblings.
     * In other words, a node should not have two or more children with the same value for "name".
     * @param name Name of the new child node.
     * @param testSuiteId ID to register the test suite. It is the caller's responsibility that this is unique in the tree!
     *                    It is advised to use <code>testSuite.UUID</code> for this ID.
     * @param testSuite The test suite proper; sometimes <code>null</code>, for example in unit tests.
     * @return An Optional containing the newly added node, or an empty Optional if there already was a child node with the given name.
     */
    public TestSuiteTree addChild(@NonNull String name, @NonNull String testSuiteId, @Nullable Suite testSuite) {
        // The field "name" should be unique among the children of a node.
        if (getChildByName(name).isPresent()) {
            return null;
        }
        // If there is not already a child node with the given name, create and add it.
        TestSuiteTree child = new TestSuiteTree(name, testSuiteId, testSuite);
        children.add(child);
        child.parent = this;
        return child;
    }

    /** Find the subtree that was registered with the given ID.
     * @param id Id of the subtree.
     * @return The subtree with the given ID, or <code>null</code> if there is no such subtree.
     */
    public TestSuiteTree findSubtree(@NonNull String id) {
        if (id.equals(testSuiteId)) {
            return this;
        }

        for (TestSuiteTree child: children) {
            TestSuiteTree searchResult = child.findSubtree(id);
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
    public Optional<TestSuiteTree> getChildByName(@NonNull String name) {
        return children
                .stream()
                .filter(treeItem->name.equals(treeItem.getName()))
                .findFirst()
                ;
    }

    /** Determine if the node is a leaf node.
     * @return 'true' if and only if the node has no child nodes.
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Make a list containing all the leaf nodes of the node.
     * @return A list with all leaf nodes that descend from this node.
     */
    List<TestSuiteTree> getLeaves() {
        List<TestSuiteTree> res = new ArrayList<>();
        if (children.isEmpty()) {
            res.add(this);
        } else {
            for (TestSuiteTree child : children) {
                res.addAll(child.getLeaves());
            }
        }
        return res;
    }

    /**
     * Detach this subtree from its parent.
     * @return `true` if and the subtree was successfully removed, or if it didn't have a parent in the first place. Returns `false` otherwise.
     */
    public boolean detach() {
        if (parent == null)
            return true;

        boolean res = parent.children.remove(this);
        if (res) {
            this.parent = null;
        }
        return res;
    }
}
