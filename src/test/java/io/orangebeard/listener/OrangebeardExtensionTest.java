package io.orangebeard.listener;

import io.orangebeard.listener.entity.StartTestItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrangebeardExtensionTest {

    @Mock
    private OrangebeardClient orangebeardClient;

    @Mock
    private ExtensionContext extensionContext;

    @InjectMocks
    private OrangebeardExtension orangebeardExtension;

    @Test
    public void before_all_test() {
        when(extensionContext.getDisplayName()).thenReturn("suitename");
        when(orangebeardClient.startSuite(any())).thenReturn(UUID.randomUUID());

        orangebeardExtension.beforeAll(extensionContext);

        verify(orangebeardClient).startSuite(any(StartTestItem.class));
    }
}