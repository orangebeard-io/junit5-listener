package io.orangebeard.listener;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrangebeardContextTest {
    private final OrangebeardContext context = OrangebeardContext.getInstance();

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
}
