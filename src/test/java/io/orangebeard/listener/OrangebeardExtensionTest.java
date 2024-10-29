package io.orangebeard.listener;

import io.orangebeard.client.entity.StartV3TestRun;

import io.orangebeard.client.entity.suite.StartSuite;
import io.orangebeard.client.entity.test.FinishTest;
import io.orangebeard.client.entity.test.StartTest;

import io.orangebeard.client.entity.test.TestStatus;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
class OrangebeardExtensionTest {

    @Mock
    private OrangebeardAsyncV3Client orangebeardClient;

    @Mock
    private ExtensionContext suiteContext;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private OrangebeardContext orangebeardContext;


    @Test
    void before_all_test() {
        when(extensionContext.getUniqueId()).thenReturn("id");

        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

        when(orangebeardClient.startTestRun(any(StartV3TestRun.class))).thenReturn(testRunUUID);
        when(orangebeardClient.startSuite(any(StartSuite.class))).thenReturn(Collections.singletonList(UUID.randomUUID()));
        when(extensionContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(extensionContext);

        verify(orangebeardClient, times(3)).startSuite(any(StartSuite.class));
    }

    @Test
    void before_all_with_multiple_suites() {
        // Test how the "beforeAll" method behaves if it needs to start multiple suites.
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");
        UUID subSuiteUUID = UUID.fromString("e9a6f895-7d8b-4baa-8564-844865567ce5");
        UUID subSubSuiteUUID = UUID.fromString("dfd80d50-b08e-4b77-bacb-eafff569b578");

        when(extensionContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
        when(extensionContext.getUniqueId()).thenReturn("id");

        when(orangebeardClient.startTestRun(any(StartV3TestRun.class))).thenReturn(testRunUUID);

        lenient().when(orangebeardClient.startSuite(argThat(s -> s != null && s.getParentSuiteUUID() == null))).thenReturn(Collections.singletonList(suiteUUID));
        lenient().when(orangebeardClient.startSuite(argThat(s -> s != null && suiteUUID.equals(s.getParentSuiteUUID())))).thenReturn(Collections.singletonList(subSuiteUUID));
        lenient().when(orangebeardClient.startSuite(argThat(s -> s != null && subSuiteUUID.equals(s.getParentSuiteUUID())))).thenReturn(Collections.singletonList(subSubSuiteUUID));

        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);
        orangebeardExtension.beforeAll(extensionContext);

        // Verify that a test run was started, *and* that all three suites were started.
        verify(orangebeardClient).startSuite(argThat(s -> s.getParentSuiteUUID() == null));
        verify(orangebeardClient).startSuite(argThat(s -> suiteUUID.equals(s.getParentSuiteUUID())));
        verify(orangebeardClient).startSuite(argThat(s -> subSuiteUUID.equals(s.getParentSuiteUUID())));
    }

    @Test
    void when_a_launch_suite_and_test_are_started_and_the_test_fails_the_failure_is_reported() {
        try(MockedStatic<OrangebeardContext> contextMockedStatic = Mockito.mockStatic(OrangebeardContext.class)) {
            contextMockedStatic.when(OrangebeardContext::getInstance).thenReturn(orangebeardContext);
            when(orangebeardContext.getTestRunUUID()).thenReturn(UUID.randomUUID());
            when(orangebeardContext.getClient()).thenReturn(orangebeardClient);


            Method method = mock(Method.class);
            when(method.getName()).thenReturn("testName");

            UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
            UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");

            when(orangebeardContext.getTestId("id")).thenReturn(testUUID);

            when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
            when(suiteContext.getUniqueId()).thenReturn("suiteId");
            when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
            when(extensionContext.getUniqueId()).thenReturn("id");
            when(extensionContext.getRequiredTestMethod()).thenReturn(method);

            when(orangebeardClient.startSuite(any())).thenReturn(Collections.singletonList(suiteUUID));
            when(orangebeardClient.startTest(any())).thenReturn(testUUID);

            OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

            orangebeardExtension.beforeAll(suiteContext);
            orangebeardExtension.beforeEach(extensionContext);
            orangebeardExtension.testFailed(extensionContext, new Exception("message"));

            verify(orangebeardClient).finishTest(eq(testUUID), any(FinishTest.class));
        }
    }

    @Test
    void when_a_test_is_not_started_but_it_does_fail_a_test_start_is_reported_before_its_failure_is_reported() {
        try(MockedStatic<OrangebeardContext> contextMockedStatic = Mockito.mockStatic(OrangebeardContext.class)) {
            contextMockedStatic.when(OrangebeardContext::getInstance).thenReturn(orangebeardContext);
            when(orangebeardContext.getTestRunUUID()).thenReturn(UUID.randomUUID());
            when(orangebeardContext.getClient()).thenReturn(orangebeardClient);
            when(orangebeardContext.getTestId(any())).thenCallRealMethod();

            Method method = mock(Method.class);
            when(method.getName()).thenReturn("testName");

            UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");

            // Mock getTestId with different responses on consecutive calls
            UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
            AtomicInteger callCount = new AtomicInteger(0);
            when(orangebeardContext.getTestId(any())).thenAnswer(invocation -> {
                if (callCount.getAndIncrement() == 0) {
                    return null; // First call returns null
                } else {
                    return testUUID; // Second call returns the test UUID
                }
            });

            when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
            when(suiteContext.getUniqueId()).thenReturn("suiteId");
            when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
            when(extensionContext.getUniqueId()).thenReturn("id");
            when(extensionContext.getRequiredTestMethod()).thenReturn(method);
            when(orangebeardClient.startSuite(any())).thenReturn(Collections.singletonList(suiteUUID));
            when(orangebeardClient.startTest(any())).thenReturn(testUUID);

            OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

            orangebeardExtension.beforeAll(suiteContext);
            orangebeardExtension.testFailed(extensionContext, new Exception("message"));

            verify(orangebeardClient, times(1)).startTest(any(StartTest.class));
            verify(orangebeardClient).finishTest(eq(testUUID), any(FinishTest.class));
        }
    }

    @Test
    void when_a_test_is_not_started_but_it_does_fail_a_test_start_is_reported_before_its_failure_is_reported_multiple_suites() {
        Method method = mock(Method.class);
        when(method.getName()).thenReturn("testName");

        UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");

        when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
        when(suiteContext.getUniqueId()).thenReturn("suiteId");
        when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
        when(extensionContext.getUniqueId()).thenReturn("id");
        when(extensionContext.getRequiredTestMethod()).thenReturn(method);
        when(orangebeardClient.startSuite(any())).thenReturn(Collections.singletonList(suiteUUID));
        when(orangebeardClient.startTest(any())).thenReturn(testUUID);
        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);

        orangebeardExtension.beforeAll(suiteContext);
        orangebeardExtension.testFailed(extensionContext, new Exception("message"));

        verify(orangebeardClient).startTest(argThat(s -> s.getSuiteUUID().equals(suiteUUID)));
        verify(orangebeardClient).finishTest(eq(testUUID), any(FinishTest.class));
    }

    @Test
    void when_a_test_is_disabled_then_finishItem_is_called() {
        // Set up the constants and the stubs.
        UUID testUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        UUID suiteUUID = UUID.fromString("27bf84ed-6269-4629-863d-0899078f8196");

        Method method = mock(Method.class);
        when(method.getName()).thenReturn("testName");

        when(suiteContext.getRequiredTestClass()).thenReturn((Class) StringBuffer.class);
        when(suiteContext.getUniqueId()).thenReturn("suiteId");
        when(extensionContext.getRequiredTestMethod()).thenReturn(method);
        when(extensionContext.getParent()).thenReturn(Optional.of(suiteContext));
        when(extensionContext.getUniqueId()).thenReturn(suiteUUID.toString());

        when(orangebeardClient.startSuite(any())).thenReturn(Collections.singletonList(suiteUUID));
        when(orangebeardClient.startTest(any())).thenReturn(testUUID);

        // Perform the test.
        OrangebeardExtension orangebeardExtension = new OrangebeardExtension(orangebeardClient);
        orangebeardExtension.beforeAll(suiteContext);
        orangebeardExtension.beforeEach(extensionContext);
        orangebeardExtension.testDisabled(extensionContext, Optional.of("Testing what happens if the test is disabled."));

        // Check the result of the test: verify that a call to `finishTest` was made, where the status of the FinishTest argument is "SKIPPED".
        ArgumentCaptor<FinishTest> argument = ArgumentCaptor.forClass(FinishTest.class);
        verify(orangebeardClient).finishTest(eq(testUUID), argument.capture());
        assertEquals(TestStatus.SKIPPED, argument.getValue().getStatus());
    }
}
