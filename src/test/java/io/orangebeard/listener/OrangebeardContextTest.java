package io.orangebeard.listener;

import io.orangebeard.client.entity.FinishV3TestRun;
import io.orangebeard.client.entity.StartV3TestRun;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrangebeardContextTest {
    private final OrangebeardContext context = OrangebeardContext.getInstance();
    private final OrangebeardAsyncV3Client client = mock(OrangebeardAsyncV3Client.class);

    @BeforeEach
    void setUp() {
        context.reset();
    }

    @AfterEach
    void tearDown() {
        context.reset();
    }

    private static StartV3TestRun run() {
        return new StartV3TestRun("set", "description", Set.of());
    }

    @Test
    void stored_tests_can_be_looked_up_by_identifier() {
        UUID testId = UUID.randomUUID();

        context.addTest("context-test-known", testId);

        assertThat(context.getTestId("context-test-known")).isEqualTo(testId);
        assertThat(context.getTestId("context-test-unknown")).isNull();
    }

    @Test
    void missing_identifiers_or_ids_are_ignored_instead_of_failing() {
        context.addTest(null, UUID.randomUUID());
        context.addTest("context-test-without-id", null);
        context.removeTest(null);

        assertThat(context.getTestId(null)).isNull();
        assertThat(context.getTestId("context-test-without-id")).isNull();
    }

    @Test
    void tests_can_be_registered_from_parallel_threads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 1000; i++) {
            String id = "context-test-parallel-" + i;
            executor.submit(() -> context.addTest(id, UUID.randomUUID()));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (int i = 0; i < 1000; i++) {
            assertThat(context.getTestId("context-test-parallel-" + i)).isNotNull();
        }
    }

    @Test
    void the_client_is_only_created_once() {
        OrangebeardAsyncV3Client first = context.ensureClient(() -> client);
        OrangebeardAsyncV3Client second = context.ensureClient(() -> mock(OrangebeardAsyncV3Client.class));

        assertThat(first).isSameAs(client);
        assertThat(second).isSameAs(client);
        assertThat(context.getClient()).isSameAs(client);
    }

    @Test
    void the_run_is_only_started_once_even_when_asked_from_many_threads() throws Exception {
        UUID run = UUID.randomUUID();
        when(client.startTestRun(any())).thenReturn(run);
        context.ensureClient(() -> client);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            executor.submit(() -> {
                go.await();
                return context.ensureRunStarted(run());
            });
        }
        go.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        verify(client, times(1)).startTestRun(any());
        assertThat(context.getTestRunUUID()).isEqualTo(run);
    }

    @Test
    void finishing_finishes_the_run_once_and_a_new_run_can_follow() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(client.startTestRun(any())).thenReturn(first, second);
        context.ensureClient(() -> client);
        context.ensureRunStarted(run());

        context.finishRun();
        context.finishRun();

        verify(client, times(1)).finishTestRun(eq(first), any(FinishV3TestRun.class));
        assertThat(context.getTestRunUUID()).isNull();
        assertThat(context.ensureRunStarted(run())).isEqualTo(second);
    }

    @Test
    void finishing_without_a_run_does_nothing() {
        context.ensureClient(() -> client);

        context.finishRun();

        verify(client, never()).finishTestRun(any(), any());
    }

    @Test
    void a_failure_while_finishing_does_not_escape() {
        when(client.startTestRun(any())).thenReturn(UUID.randomUUID());
        doThrow(new IllegalStateException("server gone")).when(client).finishTestRun(any(), any());
        context.ensureClient(() -> client);
        context.ensureRunStarted(run());

        context.finishRun();

        assertThat(context.getTestRunUUID()).isNull();
    }
}
