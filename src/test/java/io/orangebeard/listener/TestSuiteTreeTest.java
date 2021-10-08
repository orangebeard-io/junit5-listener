package io.orangebeard.listener;

import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class TestSuiteTreeTest {

    @Test
    public void when_a_child_is_requested_and_it_is_present_the_child_is_returned() {
        UUID rootUUID = UUID.fromString("096bac26-b9ae-43c3-aec5-1eebd1f96403");
        UUID childUUID = UUID.fromString("1ee1ddc5-9f95-4ac7-87f3-1e8464b9b096");
        TestSuiteTree root = new TestSuiteTree("", "rootID", rootUUID);
        TestSuiteTree expected = root.addChild("child", "childID", childUUID);

        Optional<TestSuiteTree> child2 = root.getChildByName("child");

        assertThat(child2).isPresent();
        assertThat(child2.get()).isEqualTo(expected);
    }

    @Test
    public void when_a_child_is_requested_and_it_is_absent_then_an_empty_optional_is_returned() {
        UUID rootUUID = UUID.fromString("096bac26-b9ae-43c3-aec5-1eebd1f96403");
        UUID childUUID = UUID.fromString("1ee1ddc5-9f95-4ac7-87f3-1e8464b9b096");
        TestSuiteTree root = new TestSuiteTree("", "rootID", rootUUID);
        root.addChild("child", "childID", childUUID);

        Optional<TestSuiteTree> child = root.getChildByName("nonexistent_node");

        assertThat(child.isEmpty()).isTrue();
    }

    @Test
    public void when_a_child_is_added_once_then_the_reference_is_returned() {
        UUID rootUUID = UUID.fromString("096bac26-b9ae-43c3-aec5-1eebd1f96403");
        UUID childUUID = UUID.fromString("1ee1ddc5-9f95-4ac7-87f3-1e8464b9b096");
        TestSuiteTree root = new TestSuiteTree("", "rootID", rootUUID);
        TestSuiteTree child = root.addChild("child","childID", childUUID);

        Optional<TestSuiteTree> ref = root.getChildByName("child");

        assertThat(ref).isPresent();
        assertThat(child).isEqualTo(ref.get());
    }

    @Test
    public void when_a_child_is_added_twice_then_an_empty_optional_is_returned_for_the_second_addition() {
        UUID rootUUID = UUID.fromString("096bac26-b9ae-43c3-aec5-1eebd1f96403");
        UUID child1UUID = UUID.fromString("1ee1ddc5-9f95-4ac7-87f3-1e8464b9b096");
        UUID child2UUID = UUID.fromString("fb9ac6a3-e9b6-42c7-a6ff-c42f1860db05");
        TestSuiteTree root = new TestSuiteTree("", "rootID", rootUUID);
        root.addChild("child", "childID", child1UUID);

        TestSuiteTree child2 = root.addChild("child", "childID2", child2UUID);

        assertThat(child2).isNull();
    }

    @Test
    public void when_a_descendant_is_asked_by_node_key_then_the_node_with_that_key_is_returned() {
        String idToFind = "e38edd33-6431-4f66-afe7-d4350c2e4c4c";
        String idOfRoot = "096bac26-b9ae-43c3-aec5-1eebd1f96403";
        String idOfChild1 = "781c5b1e-a6e5-4ede-9dfd-36f41ed94bec";
        String idOfChild2 = "88fba2e8-375b-4f8e-bf20-edf71dbce434";
        String idOfGrandchild1 = "20c9b62b-5d38-46ba-9172-f7e689870c09";
        String idOfGrandchild2 = "a595fab8-3876-444f-b48e-7e777bf85d65";
        String idOfGrandchild3 = "5e56a046-94fc-49e7-be05-cee03058f8cb";
        String idOfGrandchild4 = "07165b5a-f47e-49ac-b75f-678fa8f28a32";
        String idOfGreatGrandchild1 = "f213505f-40da-4644-b76b-d99439a76c74";

        TestSuiteTree root = new TestSuiteTree("", "rootID", UUID.fromString(idOfRoot));
        TestSuiteTree child1 = root.addChild("child1", idOfChild1, UUID.fromString(idOfChild1));
        child1.addChild("grandchild1", idOfGrandchild1, UUID.fromString(idOfGrandchild1));
        child1.addChild("grandchild2", idOfGrandchild2, UUID.fromString(idOfGrandchild2));
        TestSuiteTree child2 = root.addChild("child2", idOfChild2, UUID.fromString(idOfChild2));
        TestSuiteTree grandchild3 = child2.addChild("grandchild3", idOfGrandchild3, UUID.fromString(idOfGrandchild3));
        child2.addChild("grandchild4", idOfGrandchild4, UUID.fromString(idOfGrandchild4));
        // The node we are looking for, has a UUID that is different from its key. This is intentional!
        // We are testing if a descendant can be found by its key, NOT if it can be found by its UUID.
        TestSuiteTree greatGrandchild1 = grandchild3.addChild("greatGrandchild1", idToFind, UUID.fromString(idOfGreatGrandchild1));

        Optional<TestSuiteTree> actualResult = root.findSubtree(idToFind);

        assertThat(actualResult.get()).isEqualTo(greatGrandchild1);
    }

    @Test
    public void when_leaves_are_requested_then_only_nodes_without_children_are_returned() {
        String idOfRoot = "096bac26-b9ae-43c3-aec5-1eebd1f96403";
        String idOfChild1 = "781c5b1e-a6e5-4ede-9dfd-36f41ed94bec";
        String idOfChild2 = "88fba2e8-375b-4f8e-bf20-edf71dbce434";
        String idOfGrandchild1 = "20c9b62b-5d38-46ba-9172-f7e689870c09";
        String idOfGrandchild2 = "a595fab8-3876-444f-b48e-7e777bf85d65";
        TestSuiteTree root = new TestSuiteTree("", "rootID", UUID.fromString(idOfRoot));
        TestSuiteTree child1 = root.addChild("child1", idOfChild1, UUID.fromString(idOfChild1));
        TestSuiteTree grandchild1 = child1.addChild("grandchild1", idOfGrandchild1, UUID.fromString(idOfGrandchild1));
        TestSuiteTree grandchild2 = child1.addChild("grandchild2", idOfGrandchild2, UUID.fromString(idOfGrandchild2));
        TestSuiteTree child2 = root.addChild("child2", idOfChild2, UUID.fromString(idOfChild2));

        List<TestSuiteTree> leaves = root.getLeaves();

        assertThat(leaves).contains(grandchild1);
        assertThat(leaves).contains(grandchild2);
        assertThat(leaves).contains(child2);
        assertThat(leaves).doesNotContain(root);
        assertThat(leaves).doesNotContain(child1);
    }

    @Test
    public void when_asking_a_leaf_node_if_it_has_children_then_return_false() {
        String idOfRoot = "096bac26-b9ae-43c3-aec5-1eebd1f96403";
        TestSuiteTree root = new TestSuiteTree("", "rootID", UUID.fromString(idOfRoot));

        boolean hasChildren = root.hasChildren();

        assertFalse(hasChildren);
    }

    @Test
    public void when_asking_a_non_leaf_node_if_it_has_children_then_return_true() {
        String idOfRoot = "096bac26-b9ae-43c3-aec5-1eebd1f96403";
        String idOfChild1 = "781c5b1e-a6e5-4ede-9dfd-36f41ed94bec";
        TestSuiteTree root = new TestSuiteTree("", "rootID", UUID.fromString(idOfRoot));
        TestSuiteTree child1 = root.addChild("child1", idOfChild1, UUID.fromString(idOfChild1));

        boolean hasChildren = root.hasChildren();

        assertTrue(hasChildren);
    }
}
