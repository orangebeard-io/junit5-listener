package io.orangebeard.listener;

import io.orangebeard.client.entity.log.Log;

import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.orangebeard.client.entity.LogFormat.PLAIN_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
class OrangebeardExecutionListenerTest {

    @Mock
    private OrangebeardAsyncV3Client orangebeardClient;

    @Mock
    private OrangebeardContext orangebeardContext;

    @Mock
    private TestIdentifier testIdentifier;

    @Mock
    private ReportEntry entry;

    @Test
    void reporting_entry_is_published_as_log() {
        String uniqueId = "[test:uniqueId]";

        String entryString = "Test: Entry\n";
        Map<String, String> entryAsMap = new HashMap<>();
        entryAsMap.put("Test", "Entry");

        try (MockedStatic<OrangebeardContext> contextMockedStatic = Mockito.mockStatic(OrangebeardContext.class)) {
            contextMockedStatic.when(OrangebeardContext::getInstance).thenReturn(orangebeardContext);

            when(testIdentifier.getUniqueId()).thenReturn(uniqueId);
            when(entry.getKeyValuePairs()).thenReturn(entryAsMap);

            when(orangebeardContext.getTestRunUUID()).thenReturn(UUID.randomUUID());
            when(orangebeardContext.getTestId(any())).thenReturn(UUID.randomUUID());
            when(orangebeardContext.getClient()).thenReturn(orangebeardClient);

            OrangebeardExecutionListener orangebeardExecutionListener = new OrangebeardExecutionListener();
            orangebeardExecutionListener.reportingEntryPublished(testIdentifier, entry);

            ArgumentCaptor<Log> argument = ArgumentCaptor.forClass(Log.class);
            verify(orangebeardClient).log(argument.capture());
            assertEquals(entryString, argument.getValue().getMessage());
            assertEquals(PLAIN_TEXT, argument.getValue().getLogFormat());

            verify(orangebeardClient, times(1)).log(any(Log.class));
        }
    }
}
