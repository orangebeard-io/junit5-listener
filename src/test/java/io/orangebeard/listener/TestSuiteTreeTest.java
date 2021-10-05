package io.orangebeard.listener;

import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestSuiteTreeTest {

    @Test
    public void when_a_child_is_requested_and_it_is_present_the_child_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", "rootID", null);
        TestSuiteTree expected = root.addChild("child", "childID", null);

        Optional<TestSuiteTree> child2 = root.getChildByName("child");

        assertThat(child2).isPresent();
        assertThat(child2.get()).isEqualTo(expected);
    }

    @Test
    public void when_a_child_is_requested_and_it_is_absent_then_an_empty_optional_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", "rootID", null);
        root.addChild("child", "childID", null);

        Optional<TestSuiteTree> child = root.getChildByName("nonexistent_node");

        assertThat(child.isEmpty()).isTrue();
    }

    @Test
    public void when_a_child_is_added_once_then_the_reference_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", "rootID", null);
        TestSuiteTree child = root.addChild("child","childID", null);

        Optional<TestSuiteTree> ref = root.getChildByName("child");

        assertThat(ref).isPresent();
        assertThat(child).isEqualTo(ref.get());
    }

    @Test
    public void when_a_child_is_added_twice_then_an_empty_optional_is_returned_for_the_second_addition() {
        TestSuiteTree root = new TestSuiteTree("", "rootID", null);
        root.addChild("child", "childID", null);

        TestSuiteTree child2 = root.addChild("child", "childID2", null);

        assertThat(child2).isNull();
    }

    @Test
    public void when_a_descendant_is_asked_by_id_then_the_node_with_that_id_is_returned() {
        String idToFind = "e38edd33-6431-4f66-afe7-d4350c2e4c4c";
        String idOfChild1 = "781c5b1e-a6e5-4ede-9dfd-36f41ed94bec";
        String idOfChild2 = "88fba2e8-375b-4f8e-bf20-edf71dbce434";
        String idOfGrandchild1 = "20c9b62b-5d38-46ba-9172-f7e689870c09";
        String idOfGrandchild2 = "a595fab8-3876-444f-b48e-7e777bf85d65";
        String idOfGrandchild3 = "5e56a046-94fc-49e7-be05-cee03058f8cb";
        String idOfGrandchild4 = "07165b5a-f47e-49ac-b75f-678fa8f28a32";

        TestSuiteTree root = new TestSuiteTree("", "rootID", null);
        TestSuiteTree child1 = root.addChild("child1", idOfChild1, null);
        child1.addChild("grandchild1", idOfGrandchild1, null);
        child1.addChild("grandchild2", idOfGrandchild2, null);
        TestSuiteTree child2 = root.addChild("child2", idOfChild2, null);
        TestSuiteTree grandchild3 = child2.addChild("grandchild3", idOfGrandchild3, null);
        child2.addChild("grandchild4", idOfGrandchild4, null);
        TestSuiteTree greatGrandchild1 = grandchild3.addChild("greatGrandchild1", idToFind, null);

        TestSuiteTree actualResult = root.findSubtree(idToFind);

        assertThat(actualResult).isEqualTo(greatGrandchild1);
    }
}
