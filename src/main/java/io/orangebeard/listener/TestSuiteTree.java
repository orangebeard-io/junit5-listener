package io.orangebeard.listener;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tree of the test suites that were started in Orangebeard, used to find the suite a test belongs to.
 * All nodes of a tree share one index (node key to node) and one lock, so the tree can be used from the
 * threads that JUnit uses for parallel execution, and lookups by key do not need to walk the tree.
 */
class TestSuiteTree {
    /** Name of the node. Unique among siblings. */
    private final String testSuiteName;

    /**
     * Unique key for test suite at this level.
     * This is usually but not always a UUID.
     */
    private final String nodeKey;

    /** UUID for the test suite at this level. */
    private final UUID testSuiteUUID;
    /** Child nodes. */
    private final List<TestSuiteTree> children = new ArrayList<>();
    /** Node key to node, for all nodes in the tree. Also the lock that guards the whole tree. */
    private final Map<String, TestSuiteTree> index;
    /** Reference to the parent node. */
    private TestSuiteTree parent;

    /**
     * Construct a new Tree of test suites.
     * @param name Name of the root node.
     * @param nodeKey Key of the test suite. Often a UUID in String form, but not always.
     * @param testSuiteUUID UUID for the test suite at this level.
     */
    public TestSuiteTree(@NonNull String name, @NonNull String nodeKey, @NonNull UUID testSuiteUUID) {
        this(name, nodeKey, testSuiteUUID, new HashMap<>());
    }

    private TestSuiteTree(String name, String nodeKey, UUID testSuiteUUID, Map<String, TestSuiteTree> index) {
        this.testSuiteName = name;
        this.nodeKey = nodeKey;
        this.testSuiteUUID = testSuiteUUID;
        this.index = index;
        index.put(nodeKey, this);
    }

    public String getName() { return testSuiteName; }
    public String getNodeKey() { return nodeKey; }
    public UUID getTestSuiteUUID() { return testSuiteUUID; }

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
    public Optional<TestSuiteTree> addChild(@NonNull String name, @NonNull String nodeKey, @NonNull UUID testSuiteUUID) {
        synchronized (index) {
            // The field "name" should be unique among the children of a node.
            if (getChildByName(name).isPresent()) {
                return Optional.empty();
            }
            // If there is not already a child node with the given name, create and add it.
            TestSuiteTree child = new TestSuiteTree(name, nodeKey, testSuiteUUID, index);
            children.add(child);
            child.parent = this;
            return Optional.of(child);
        }
    }

    /**
     * Find the subtree that was registered with the given ID.
     * @param id ID of the subtree.
     * @return An Optional containing the subtree with the given ID. If there is no such subtree, returns an empty Optional.
     */
    public Optional<TestSuiteTree> findSubtree(@NonNull String id) {
        synchronized (index) {
            TestSuiteTree found = index.get(id);
            return found != null && found.isSelfOrDescendantOf(this) ? Optional.of(found) : Optional.empty();
        }
    }

    /**
     * Finds the direct child node with the given name; or <code>null</code> if there is no such node.
     * Names of child nodes should be unique among their siblings.
     * @param name Name of the requested child node.
     * @return An Optional containing the child node with the given name; or an empty Optional if there is no such node.
     */
    public Optional<TestSuiteTree> getChildByName(@NonNull String name) {
        synchronized (index) {
            return children
                    .stream()
                    .filter(treeItem -> name.equals(treeItem.getName()))
                    .findFirst();
        }
    }

    /**
     * Runs an action while holding the lock of this tree, so a lookup and the additions that depend on it
     * cannot be interleaved with those of another thread.
     */
    <T> T locked(java.util.function.Supplier<T> action) {
        synchronized (index) {
            return action.get();
        }
    }

    private boolean isSelfOrDescendantOf(TestSuiteTree ancestor) {
        for (TestSuiteTree node = this; node != null; node = node.parent) {
            if (node == ancestor) {
                return true;
            }
        }
        return false;
    }
}
