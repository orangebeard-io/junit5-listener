package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.entity.log.Log;
import io.orangebeard.client.entity.log.LogLevel;
import io.orangebeard.client.entity.suite.StartSuite;
import io.orangebeard.client.entity.test.FinishTest;
import io.orangebeard.client.entity.test.StartTest;
import io.orangebeard.client.entity.test.TestStatus;
import io.orangebeard.client.entity.test.TestType;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrangebeardExtensionTest {
    private static final UUID RUN = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");

    private final OrangebeardAsyncV3Client client = mock(OrangebeardAsyncV3Client.class);
    private OrangebeardExtension extension;

    @BeforeEach
    void setUp() {
        OrangebeardContext.getInstance().reset();
        when(client.startTestRun(any())).thenReturn(RUN);
        extension = new OrangebeardExtension(client, new OrangebeardProperties("https://host", UUID.randomUUID(), "project", "set"));
    }

    @AfterEach
    void tearDown() {
        OrangebeardContext.getInstance().reset();
    }

    private static ExtensionContext classContext(Class<?> testClass, String uniqueId) {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getUniqueId()).thenReturn(uniqueId);
        when(context.getDisplayName()).thenReturn(testClass.getSimpleName());
        when(context.getRequiredTestClass()).thenAnswer(invocation -> testClass);
        return context;
    }

    private static ExtensionContext methodContext(ExtensionContext classContext, Class<?> testClass, String methodName) {
        Method method = mock(Method.class);
        when(method.getName()).thenReturn(methodName);
        String uniqueId = classContext.getUniqueId() + "/[method:" + methodName + "()]";
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getUniqueId()).thenReturn(uniqueId);
        when(context.getDisplayName()).thenReturn(methodName + "()");
        when(context.getParent()).thenReturn(Optional.of(classContext));
        when(context.getTestMethod()).thenReturn(Optional.of(method));
        when(context.getTestClass()).thenAnswer(invocation -> Optional.of(testClass));
        return context;
    }

    private List<UUID> suiteIdsFor(int count) {
        List<UUID> ids = IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
        when(client.startSuite(any())).thenReturn(ids);
        return ids;
    }

    @Test
    void the_suites_of_a_class_are_started_with_one_request() {
        suiteIdsFor(3);

        extension.beforeAll(classContext(StringBuffer.class, "class-1"));

        ArgumentCaptor<StartSuite> suite = ArgumentCaptor.forClass(StartSuite.class);
        verify(client).startSuite(suite.capture());
        assertThat(suite.getValue().getSuiteNames()).containsExactly("java", "lang", "StringBuffer");
        assertThat(suite.getValue().getParentSuiteUUID()).isNull();
        assertThat(suite.getValue().getTestRunUUID()).isEqualTo(RUN);
    }

    @Test
    void only_the_suites_that_are_not_started_yet_are_started_below_their_parent() {
        List<UUID> first = suiteIdsFor(3);
        extension.beforeAll(classContext(StringBuffer.class, "class-1"));
        suiteIdsFor(1);

        extension.beforeAll(classContext(StringBuilder.class, "class-2"));
        extension.beforeAll(classContext(StringBuffer.class, "class-1-again"));

        ArgumentCaptor<StartSuite> suite = ArgumentCaptor.forClass(StartSuite.class);
        verify(client, times(2)).startSuite(suite.capture());
        StartSuite second = suite.getAllValues().get(1);
        assertThat(second.getSuiteNames()).containsExactly("StringBuilder");
        assertThat(second.getParentSuiteUUID()).isEqualTo(first.get(1));
    }

    @Test
    void a_test_is_started_in_the_suite_of_its_class_and_finished_with_its_result() {
        List<UUID> suites = suiteIdsFor(3);
        UUID testId = UUID.randomUUID();
        when(client.startTest(any())).thenReturn(testId);
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");

        extension.beforeAll(classContext);
        extension.beforeEach(test);
        extension.testSuccessful(test);

        ArgumentCaptor<StartTest> start = ArgumentCaptor.forClass(StartTest.class);
        verify(client).startTest(start.capture());
        assertThat(start.getValue().getSuiteUUID()).isEqualTo(suites.get(2));
        assertThat(start.getValue().getTestName()).isEqualTo("append()");
        assertThat(start.getValue().getTestType()).isEqualTo(TestType.TEST);
        assertThat(start.getValue().getDescription()).isEqualTo("java.lang.StringBuffer.append");
        ArgumentCaptor<FinishTest> finish = ArgumentCaptor.forClass(FinishTest.class);
        verify(client).finishTest(eq(testId), finish.capture());
        assertThat(finish.getValue().getStatus()).isEqualTo(TestStatus.PASSED);
        assertThat(OrangebeardContext.getInstance().getTestId(test.getUniqueId())).as("forgotten once finished").isNull();
    }

    @Test
    void a_failed_test_logs_the_message_and_stack_trace_as_errors() {
        suiteIdsFor(3);
        UUID testId = UUID.randomUUID();
        when(client.startTest(any())).thenReturn(testId);
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");
        extension.beforeAll(classContext);
        extension.beforeEach(test);

        extension.testFailed(test, new AssertionError("expected 1 but was 2"));

        ArgumentCaptor<Log> logs = ArgumentCaptor.forClass(Log.class);
        verify(client, times(2)).log(logs.capture());
        assertThat(logs.getAllValues()).extracting(Log::getLogLevel).containsOnly(LogLevel.ERROR);
        assertThat(logs.getAllValues().get(0).getMessage()).isEqualTo("expected 1 but was 2");
        assertThat(logs.getAllValues().get(1).getMessage()).contains("AssertionError", "expected 1 but was 2", "at ");
        assertThat(logs.getAllValues()).extracting(Log::getTestUUID).containsOnly(testId);
        ArgumentCaptor<FinishTest> finish = ArgumentCaptor.forClass(FinishTest.class);
        verify(client).finishTest(eq(testId), finish.capture());
        assertThat(finish.getValue().getStatus()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void a_failure_without_a_message_is_logged_with_the_name_of_the_exception() {
        suiteIdsFor(3);
        when(client.startTest(any())).thenReturn(UUID.randomUUID());
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");
        extension.beforeAll(classContext);
        extension.beforeEach(test);

        extension.testFailed(test, new NullPointerException());

        ArgumentCaptor<Log> logs = ArgumentCaptor.forClass(Log.class);
        verify(client, times(2)).log(logs.capture());
        assertThat(logs.getAllValues().get(0).getMessage()).isEqualTo("java.lang.NullPointerException");
    }

    @Test
    void an_aborted_test_is_stopped_and_its_cause_logged_as_a_warning() {
        suiteIdsFor(3);
        UUID testId = UUID.randomUUID();
        when(client.startTest(any())).thenReturn(testId);
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");
        extension.beforeAll(classContext);
        extension.beforeEach(test);

        extension.testAborted(test, new IllegalStateException("assumption failed"));

        ArgumentCaptor<Log> logs = ArgumentCaptor.forClass(Log.class);
        verify(client, times(2)).log(logs.capture());
        assertThat(logs.getAllValues()).extracting(Log::getLogLevel).containsOnly(LogLevel.WARN);
        ArgumentCaptor<FinishTest> finish = ArgumentCaptor.forClass(FinishTest.class);
        verify(client).finishTest(eq(testId), finish.capture());
        assertThat(finish.getValue().getStatus()).isEqualTo(TestStatus.STOPPED);
    }

    @Test
    void a_test_that_fails_before_it_was_started_is_started_first() {
        suiteIdsFor(3);
        UUID testId = UUID.randomUUID();
        when(client.startTest(any())).thenReturn(testId);
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");
        extension.beforeAll(classContext);

        extension.testFailed(test, new RuntimeException("initialization failed"));

        verify(client).startTest(any());
        verify(client).finishTest(eq(testId), any());
    }

    @Test
    void a_disabled_test_is_reported_as_skipped_with_its_reason() {
        suiteIdsFor(3);
        UUID testId = UUID.randomUUID();
        when(client.startTest(any())).thenReturn(testId);
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");
        extension.beforeAll(classContext);

        extension.testDisabled(test, Optional.of("not today"));

        ArgumentCaptor<Log> log = ArgumentCaptor.forClass(Log.class);
        verify(client).log(log.capture());
        assertThat(log.getValue().getMessage()).isEqualTo("not today");
        assertThat(log.getValue().getLogLevel()).isEqualTo(LogLevel.WARN);
        ArgumentCaptor<FinishTest> finish = ArgumentCaptor.forClass(FinishTest.class);
        verify(client).finishTest(eq(testId), finish.capture());
        assertThat(finish.getValue().getStatus()).isEqualTo(TestStatus.SKIPPED);
    }

    @Test
    void a_failing_beforeAll_is_reported_as_a_failed_setup_test_and_still_fails_the_class() {
        List<UUID> suites = suiteIdsFor(3);
        UUID setupId = UUID.randomUUID();
        when(client.startTest(any())).thenReturn(setupId);
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        extension.beforeAll(classContext);
        IllegalStateException failure = new IllegalStateException("database is down");

        assertThatThrownBy(() -> extension.handleBeforeAllMethodExecutionException(classContext, failure)).isSameAs(failure);

        ArgumentCaptor<StartTest> start = ArgumentCaptor.forClass(StartTest.class);
        verify(client).startTest(start.capture());
        assertThat(start.getValue().getSuiteUUID()).isEqualTo(suites.get(2));
        assertThat(start.getValue().getTestType()).isEqualTo(TestType.BEFORE);
        verify(client, times(2)).log(any(Log.class));
        ArgumentCaptor<FinishTest> finish = ArgumentCaptor.forClass(FinishTest.class);
        verify(client).finishTest(eq(setupId), finish.capture());
        assertThat(finish.getValue().getStatus()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void a_failing_afterAll_is_reported_as_a_failed_teardown_test() {
        suiteIdsFor(3);
        when(client.startTest(any())).thenReturn(UUID.randomUUID());
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        extension.beforeAll(classContext);

        assertThatThrownBy(() -> extension.handleAfterAllMethodExecutionException(classContext, new RuntimeException("cleanup failed")))
                .hasMessage("cleanup failed");

        ArgumentCaptor<StartTest> start = ArgumentCaptor.forClass(StartTest.class);
        verify(client).startTest(start.capture());
        assertThat(start.getValue().getTestType()).isEqualTo(TestType.AFTER);
    }

    @Test
    void nothing_is_reported_and_nothing_fails_when_the_suite_could_not_be_started() {
        when(client.startSuite(any())).thenReturn(List.of());
        ExtensionContext classContext = classContext(StringBuffer.class, "class-1");
        ExtensionContext test = methodContext(classContext, StringBuffer.class, "append");

        extension.beforeAll(classContext);
        extension.beforeEach(test);
        extension.testSuccessful(test);
        extension.testFailed(test, new RuntimeException("x"));

        verify(client, never()).startTest(any());
        verify(client, never()).finishTest(any(), any());
    }

    @Test
    void a_test_without_parent_context_is_not_reported() {
        ExtensionContext orphan = mock(ExtensionContext.class);
        when(orphan.getParent()).thenReturn(Optional.empty());
        when(orphan.getDisplayName()).thenReturn("orphan");
        when(orphan.getUniqueId()).thenReturn("orphan");

        extension.beforeEach(orphan);
        extension.testDisabled(orphan, Optional.empty());

        verify(client, never()).startTest(any());
    }

    @Test
    void anonymous_classes_have_a_suite_path_too() {
        suiteIdsFor(5);
        Object anonymous = new Object() { };

        extension.beforeAll(classContext(anonymous.getClass(), "anonymous"));

        ArgumentCaptor<StartSuite> suite = ArgumentCaptor.forClass(StartSuite.class);
        verify(client).startSuite(suite.capture());
        assertThat(suite.getValue().getSuiteNames()).last().isEqualTo("1");
    }
}
