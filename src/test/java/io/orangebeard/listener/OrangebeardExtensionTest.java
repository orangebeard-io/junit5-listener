package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardClient;
import io.orangebeard.client.entity.FinishTestItem;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrangebeardExtensionTest {

    @Mock
    private OrangebeardClient orangebeardClient;

    @Mock
    private ExtensionContext suiteContext;

    @Mock
    private ExtensionContext extensionContext;

    @Test
    public void before_all_test() {
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(orangebeardClient.startTestRun(any(StartTestRun.class))).thenReturn(testRunUUID);
        when(extensionContext.getDisplayName()).thenReturn("suitename");
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(UUID.randomUUID());

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(extensionContext);

        verify(orangebeardClient).startTestItem(eq(null), any(StartTestItem.class));
    }


    @Test
    public void when_a_launch_suite_and_test_are_started_and_the_test_fails_the_failure_is_reported() {
        Method method = mock(Method.class);
        when(method.getName()).thenReturn("testName");

        UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(suiteContext.getUniqueId()).thenReturn("suiteId");
        when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
        when(extensionContext.getUniqueId()).thenReturn("id");
        when(extensionContext.getRequiredTestMethod()).thenReturn(method);
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(testUUID);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(suiteContext);
        orangebeardExtension.beforeEach(extensionContext);
        orangebeardExtension.testFailed(extensionContext, new Exception("message"));

        verify(orangebeardClient).finishTestItem(eq(testUUID), any(FinishTestItem.class));
    }

    @Test
    public void when_a_test_is_not_started_but_it_does_fail_a_test_start_is_reported_before_its_failure_is_reported() {
        Method method = mock(Method.class);
        when(method.getName()).thenReturn("testName");

        UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(suiteContext.getUniqueId()).thenReturn("suiteId");
        when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
        when(extensionContext.getUniqueId()).thenReturn("id");
        when(extensionContext.getRequiredTestMethod()).thenReturn(method);
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(testUUID);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(suiteContext);
        orangebeardExtension.testFailed(extensionContext, new Exception("message"));

        verify(orangebeardClient).startTestItem(eq(testUUID), any(StartTestItem.class));
        verify(orangebeardClient).finishTestItem(eq(testUUID), any(FinishTestItem.class));
    }

    @Test
    public void after_all_test() {
        UUID suiteUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(extensionContext.getUniqueId()).thenReturn("id");
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(suiteUUID);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(extensionContext);
        orangebeardExtension.afterAll(extensionContext);

        verify(orangebeardClient).finishTestItem(eq(suiteUUID), any(FinishTestItem.class));
    }
}
