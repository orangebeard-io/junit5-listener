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
import static org.mockito.Mockito.times;
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
        //when(extensionContext.getDisplayName()).thenReturn("suitename");
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(UUID.randomUUID());
        when(extensionContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(extensionContext);

        verify(orangebeardClient).startTestItem(eq(null), any(StartTestItem.class));
    }

    @Test
    public void before_all_with_multiple_suites() {
        // Test how the "beforeAll" method behaves if it needs to start multiple suites.
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");
        UUID subSuiteUUID = UUID.fromString("e9a6f895-7d8b-4baa-8564-844865567ce5");
        UUID subSubSuiteUUID = UUID.fromString("dfd80d50-b08e-4b77-bacb-eafff569b578");
        when(orangebeardClient.startTestRun(any(StartTestRun.class))).thenReturn(testRunUUID);
        when(extensionContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
        when(orangebeardClient.startTestItem(eq(null), any())).thenReturn(suiteUUID);
        when(orangebeardClient.startTestItem(eq(suiteUUID), any())).thenReturn(subSuiteUUID);
        when(orangebeardClient.startTestItem(eq(subSuiteUUID), any())).thenReturn(subSubSuiteUUID);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);
        orangebeardExtension.beforeAll(extensionContext);

        // Verify that a test run was started, *and* that all three suites were started.
        verify(orangebeardClient).startTestItem(eq(null), any(StartTestItem.class));
        verify(orangebeardClient).startTestItem(eq(suiteUUID), any(StartTestItem.class));
        verify(orangebeardClient).startTestItem(eq(subSuiteUUID), any(StartTestItem.class));
    }


    @Test
    public void when_a_launch_suite_and_test_are_started_and_the_test_fails_the_failure_is_reported() {
        Method method = mock(Method.class);
        when(method.getName()).thenReturn("testName");

        UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
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

        when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
        when(suiteContext.getUniqueId()).thenReturn("suiteId");
        when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
        when(extensionContext.getUniqueId()).thenReturn("id");
        when(extensionContext.getRequiredTestMethod()).thenReturn(method);
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(testUUID);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(suiteContext);
        orangebeardExtension.testFailed(extensionContext, new Exception("message"));

        // Ideally, we would only have one invocation of "startTestItem" in this unit test, not three.
        // This could be achieved if we could make ExtensionContext.getRequiredTestClass() return a class that is not in a package, or in a top-level package.
        // It would be even better if we could just mock "OrangebeardExtension.getCanonicalName()", to make it return whatever array we wanted.
        verify(orangebeardClient, times(3)).startTestItem(eq(testUUID), any(StartTestItem.class));
        verify(orangebeardClient).finishTestItem(eq(testUUID), any(FinishTestItem.class));
    }

    @Test
    public void when_a_test_is_not_started_but_it_does_fail_a_test_start_is_reported_before_its_failure_is_reported_multiple_suites() {
        Method method = mock(Method.class);
        when(method.getName()).thenReturn("testName");

        UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");
        UUID subSuiteUUID = UUID.fromString("e9a6f895-7d8b-4baa-8564-844865567ce5");
        UUID subSubSuiteUUID = UUID.fromString("dfd80d50-b08e-4b77-bacb-eafff569b578");

        when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
        when(suiteContext.getUniqueId()).thenReturn("suiteId");
        when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
        when(extensionContext.getUniqueId()).thenReturn("id");
        when(extensionContext.getRequiredTestMethod()).thenReturn(method);
        //when(orangebeardClient.startTestItem(any(), any())).thenReturn(testUUID);
        when(orangebeardClient.startTestItem(eq(null), any())).thenReturn(testUUID);
        when(orangebeardClient.startTestItem(eq(testUUID), any())).thenReturn(suiteUUID);
        when(orangebeardClient.startTestItem(eq(suiteUUID), any())).thenReturn(subSuiteUUID);
        when(orangebeardClient.startTestItem(eq(subSuiteUUID), any())).thenReturn(subSubSuiteUUID);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(suiteContext);
        orangebeardExtension.testFailed(extensionContext, new Exception("message"));

        //verify(orangebeardClient).startTestItem(eq(testUUID), any(StartTestItem.class));
        //verify(orangebeardClient).finishTestItem(eq(testUUID), any(FinishTestItem.class));
        verify(orangebeardClient).startTestItem(eq(subSuiteUUID), any(StartTestItem.class));
        verify(orangebeardClient).finishTestItem(eq(subSubSuiteUUID), any(FinishTestItem.class)); //TODO?~ Is this correct? Shouldn't we test if subSuiteUUID was finished? Or only subSubSuiteUUID?

    }

    @Test
    public void after_all_test() {
        UUID suiteUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(extensionContext.getUniqueId()).thenReturn("id");
        when(orangebeardClient.startTestItem(any(), any())).thenReturn(suiteUUID);
        when(extensionContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(extensionContext);
        orangebeardExtension.afterAll(extensionContext);

        // Ideally, we would only have one invocation of "startTestItem" in this unit test, not two.
        // This could be achieved if we could make ExtensionContext.getRequiredTestClass() return a class that is not in a package, or in a top-level package.
        // It would be even better if we could just mock "OrangebeardExtension.getCanonicalName()", to make it return whatever array we wanted.
        verify(orangebeardClient, times(2)).finishTestItem(eq(suiteUUID), any(FinishTestItem.class));
    }

    @Test
    public void after_all_test_multiple_suites() {
        UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");
        UUID subSuiteUUID = UUID.fromString("e9a6f895-7d8b-4baa-8564-844865567ce5");
        UUID subSubSuiteUUID = UUID.fromString("dfd80d50-b08e-4b77-bacb-eafff569b578");

        when(orangebeardClient.startTestItem(eq(null), any())).thenReturn(suiteUUID);
        when(orangebeardClient.startTestItem(eq(suiteUUID), any())).thenReturn(subSuiteUUID);
        when(orangebeardClient.startTestItem(eq(subSuiteUUID), any())).thenReturn(subSubSuiteUUID);
        when(extensionContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);
        orangebeardExtension.beforeAll(extensionContext);
        orangebeardExtension.afterAll(extensionContext);

        verify(orangebeardClient).finishTestItem(eq(suiteUUID), any(FinishTestItem.class));
        verify(orangebeardClient).finishTestItem(eq(subSuiteUUID), any(FinishTestItem.class));
        verify(orangebeardClient).finishTestItem(eq(subSubSuiteUUID), any(FinishTestItem.class));
    }
}
