package io.orangebeard.listener;

import io.orangebeard.listener.entity.Attribute;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrangebeardPropertiesTest {

    @Test
    public void property_file_is_read_correctly() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties("orangebeard.properties");

        assertThat(orangebeardProperties.requiredValuesArePresent()).isTrue();
        assertThat(orangebeardProperties.isPropertyFilePresent()).isTrue();

        assertThat(orangebeardProperties.getEndpoint()).isEqualTo("https://company.orangebeard.app");
        assertThat(orangebeardProperties.getAccessToken()).isEqualTo(UUID.fromString("043584a0-8081-4270-a32a-ad79ead2dc34"));
        assertThat(orangebeardProperties.getTestset()).isEqualTo("piet_TEST_EXAMPLE");
        assertThat(orangebeardProperties.getProjectName()).isEqualTo("piet_personal");
        assertThat(orangebeardProperties.getDescription()).isEqualTo("My awesome testrun");
        assertThat(orangebeardProperties.getAttributes()).containsOnly(new Attribute("key", "value"), new Attribute("value"));
    }

    @Test
    public void when_no_property_file_is_present_no_exception_is_thrown() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties("piet.properties");

        assertThat(orangebeardProperties.requiredValuesArePresent()).isFalse();
        assertThat(orangebeardProperties.isPropertyFilePresent()).isFalse();
    }
}
