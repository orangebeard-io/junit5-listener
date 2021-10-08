package io.orangebeard.listener;

import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class TestSuiteTree {
    /** Name of the node. Unique among siblings. */
    private final String testSuiteName;

    /** Unique key for test suite at this level.
     * This is usually but not always a UUID.
     */
    private final String nodeKey;

    /** UUID for the test suite at this level. */
    private final UUID testSuiteUUID;

    /** Reference to the parent node. */
    private TestSuiteTree parent;

    /** Child nodes. */
    private final List<TestSuiteTree> children = new ArrayList<>();

    /** Construct a new Tree of test suites.
     * @param name Name of the root node.
     * @param nodeKey Key of the test suite. Often a UUID in String form, but not always.
     * @param testSuiteUUID UUID for the test suite at this level.
     */
    public TestSuiteTree(@NonNull String name, @NonNull String nodeKey, @NonNull UUID testSuiteUUID) {
        this.testSuiteName = name;
        this.nodeKey = nodeKey;
        this.testSuiteUUID = testSuiteUUID;
    }

    public String getName() { return testSuiteName; }
    public String getNodeKey() { return nodeKey; }
    public UUID getTestSuiteUUID() { return testSuiteUUID; }

    /**
     * Test if a node has child nodes.
     * @return True if and only if the node has child nodes.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("(%s, %s, %s)", nodeKey, testSuiteName, testSuiteUUID);
    }

    /**
     * Add a child node to the given tree node.
     * The name of the child node must be unique among its siblings.
     * In other words, a node should not have two or more children with the same value for "name".
     * @param name Name of the new child node.
     * @param nodeKey ID to register the test suite. It is the caller's responsibility that this is unique in the tree!
     *                    It is advised to use <code>testSuite.UUID</code> for this ID.
     * @param testSuiteUUID UUID of the test suite.
     * @return An Optional containing the newly added node, or an empty Optional if there already was a child node with the given name.
     */
    public TestSuiteTree addChild(@NonNull String name, @NonNull String nodeKey, @NonNull UUID testSuiteUUID) {
        // The field "name" should be unique among the children of a node.
        if (getChildByName(name).isPresent()) {
            return null;
        }
        // If there is not already a child node with the given name, create and add it.
        TestSuiteTree child = new TestSuiteTree(name, nodeKey, testSuiteUUID);
        children.add(child);
        child.parent = this;
        return child;
    }

    /** Find the subtree that was registered with the given ID.
     * @param id Id of the subtree.
     * @return The subtree with the given ID, or <code>null</code> if there is no such subtree.
     */
    public Optional<TestSuiteTree> findSubtree(@NonNull String id) {
        if (id.equals(nodeKey)) {
            return Optional.of(this);
        }

        for (TestSuiteTree child: children) {
            return child.findSubtree(id);

        }
        return Optional.empty();
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
