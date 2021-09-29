package io.orangebeard.listener;

import org.assertj.core.api.Assertions;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class TestSuiteTreeTest {

    @Test
    public void when_a_child_is_requested_and_it_is_present_the_child_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        TestSuiteTree expected = root.addChild("child", null);
        Optional<TestSuiteTree> child2 = root.getChildByName("child");
        Assertions.assertThat(child2.isPresent()).isTrue();
        TestSuiteTree actual = child2.get();
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void when_a_child_is_requested_and_it_is_absent_then_an_empty_optional_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        root.addChild("child", null);
        Optional<TestSuiteTree> child = root.getChildByName("nonexistent_node");
        Assertions.assertThat(child.isEmpty()).isTrue();
    }

    @Test
    public void when_a_child_is_added_once_then_the_reference_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        TestSuiteTree child = root.addChild("child", null);
        Optional<TestSuiteTree> ref = root.getChildByName("child");
        Assertions.assertThat(ref.isPresent()).isTrue();
        Assertions.assertThat(child).isEqualTo(ref.get());
    }

    @Test
    public void when_a_child_is_added_twice_then_an_empty_optional_is_returned_for_the_second_addition() {
        TestSuiteTree root = new TestSuiteTree("", null);
        root.addChild("child", null);
        TestSuiteTree child2 = root.addChild("child", null);
        Assertions.assertThat(child2).isNull();
    }

    @Test
    public void when_a_descendant_is_asked_by_uuid_then_the_node_with_that_uuid_is_returned() {

        UUID uuidToFind = UUID.fromString("e38edd33-6431-4f66-afe7-d4350c2e4c4c");
        System.out.println(UUID.randomUUID());

        TestSuiteTree root = new TestSuiteTree("", null);
        TestSuiteTree child1 = root.addChild("child1", UUID.fromString("781c5b1e-a6e5-4ede-9dfd-36f41ed94bec"));
        child1.addChild("grandchild1", UUID.fromString("20c9b62b-5d38-46ba-9172-f7e689870c09"));
        child1.addChild("grandchild2", UUID.fromString("a595fab8-3876-444f-b48e-7e777bf85d65"));
        TestSuiteTree child2 = root.addChild("child2", UUID.fromString("88fba2e8-375b-4f8e-bf20-edf71dbce434"));
        TestSuiteTree grandchild3 = child2.addChild("grandchild3", UUID.fromString("5e56a046-94fc-49e7-be05-cee03058f8cb"));
        child2.addChild("grandchild4", UUID.fromString("07165b5a-f47e-49ac-b75f-678fa8f28a32"));

        TestSuiteTree actualResult = root.findSubtree(uuidToFind);
        TestSuiteTree expectedResult = grandchild3.addChild("greatgrandchild1", uuidToFind);

        Assertions.assertThat(actualResult).isEqualTo(expectedResult);
    }
}
