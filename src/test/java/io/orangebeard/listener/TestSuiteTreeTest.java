package io.orangebeard.listener;

import org.assertj.core.api.Assertions;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
public class TestSuiteTreeTest {

    @Test
    public void when_a_child_is_requested_and_it_is_present_the_child_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        TestSuiteTree child1 = root.addChild("child", null);
        Assertions.assertThat(root.getChildByName("child")).isEqualTo(child1);
    }

    @Test
    public void when_a_child_is_requested_and_it_is_absent_null_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        root.addChild("child", null);
        Assertions.assertThat( root.getChildByName("nonexistent_node")).isEqualTo(null);
    }

    @Test
    public void when_a_child_is_added_once_then_the_reference_is_returned() {
        TestSuiteTree root = new TestSuiteTree("", null);
        TestSuiteTree child = root.addChild("child", null);
        Assertions.assertThat(child).isNotNull();
        TestSuiteTree ref = root.getChildByName("child");
        Assertions.assertThat(child).isEqualTo(ref);
    }

    @Test
    public void when_a_child_is_added_twice_then_null_is_returned_for_the_second_addition() {
        TestSuiteTree root = new TestSuiteTree("", null);
        root.addChild("child", null);
        TestSuiteTree child2 = root.addChild("child", null);
        Assertions.assertThat(child2).isNull();
    }
}
