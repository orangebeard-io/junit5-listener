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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrangebeardExtensionTest {

    @Mock
    private OrangebeardClient orangebeardClient;

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

        verify(orangebeardClient).startTestItem(eq(testRunUUID), any(StartTestItem.class));
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
