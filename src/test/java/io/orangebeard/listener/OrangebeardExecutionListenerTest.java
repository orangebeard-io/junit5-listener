package io.orangebeard.listener;

import io.orangebeard.client.entity.LogFormat;
import io.orangebeard.client.entity.log.Log;
import io.orangebeard.client.entity.log.LogLevel;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrangebeardExecutionListenerTest {
    private final OrangebeardContext context = OrangebeardContext.getInstance();
    private final OrangebeardAsyncV3Client client = mock(OrangebeardAsyncV3Client.class);
    private final TestIdentifier testIdentifier = mock(TestIdentifier.class);

    @BeforeEach
    void setUp() {
        context.reset();
        context.setClient(client);
        when(testIdentifier.getUniqueId()).thenReturn("[test:uniqueId]");
    }

    @AfterEach
    void tearDown() {
        context.reset();
    }

    @Test
    void a_published_report_entry_is_logged_to_the_test() {
        UUID run = UUID.randomUUID();
        UUID test = UUID.randomUUID();
        context.setTestRunUUID(run);
        context.addTest("[test:uniqueId]", test);

        new OrangebeardExecutionListener().reportingEntryPublished(testIdentifier, ReportEntry.from("Test", "Entry"));

        ArgumentCaptor<Log> log = ArgumentCaptor.forClass(Log.class);
        verify(client).log(log.capture());
        assertThat(log.getValue().getMessage()).isEqualTo("Test: Entry\n");
        assertThat(log.getValue().getLogFormat()).isEqualTo(LogFormat.PLAIN_TEXT);
        assertThat(log.getValue().getLogLevel()).isEqualTo(LogLevel.INFO);
        assertThat(log.getValue().getTestRunUUID()).isEqualTo(run);
        assertThat(log.getValue().getTestUUID()).isEqualTo(test);
    }

    @Test
    void entries_for_tests_that_are_not_reported_are_ignored() {
        context.setTestRunUUID(UUID.randomUUID());

        new OrangebeardExecutionListener().reportingEntryPublished(testIdentifier, ReportEntry.from("Test", "Entry"));

        verify(client, never()).log(any());
    }

    @Test
    void entries_are_ignored_while_there_is_no_test_run() {
        context.addTest("[test:uniqueId]", UUID.randomUUID());

        new OrangebeardExecutionListener().reportingEntryPublished(testIdentifier, ReportEntry.from("Test", "Entry"));

        verify(client, never()).log(any());
    }
}
