package io.orangebeard.listener;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class TestSuiteTreeTest {

    @Test
    public void when_a_child_is_requested_and_it_is_present_the_child_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        Optional<TestSuiteTree> child1 = root.addChild("child", null);
        Optional<TestSuiteTree> child2 = root.getChildByName("child");
        Assertions.assertThat(child1.isPresent()).isTrue();
        Assertions.assertThat(child2.isPresent()).isTrue();
        Assertions.assertThat(child2.get()).isEqualTo(child1.get());
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
        Optional<TestSuiteTree> child = root.addChild("child", null);
        Assertions.assertThat(child.isPresent()).isTrue();
        Optional<TestSuiteTree> ref = root.getChildByName("child");
        Assertions.assertThat(ref.isPresent()).isTrue();
        Assertions.assertThat(child.get()).isEqualTo(ref.get());
    }

    @Test
    public void when_a_child_is_added_twice_then_an_empty_optional_is_returned_for_the_second_addition() {
        TestSuiteTree root = new TestSuiteTree("", null);
        root.addChild("child", null);
        Optional<TestSuiteTree> child2 = root.addChild("child", null);
        Assertions.assertThat(child2.isEmpty()).isTrue();
    }
}
