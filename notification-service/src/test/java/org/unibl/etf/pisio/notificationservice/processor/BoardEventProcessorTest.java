package org.unibl.etf.pisio.notificationservice.processor;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.pisio.notificationservice.config.ServiceBusProperties;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.service.ActivityIngestService;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardEventProcessorTest {

    private static final ServiceBusProperties PROPERTIES =
            new ServiceBusProperties(true, null, "Endpoint=sb://localhost", "board-events", "notifications");

    @Mock
    private ServiceBusClientBuilder.ServiceBusProcessorClientBuilder builder;

    @Mock
    private ServiceBusProcessorClient client;

    @Mock
    private ActivityIngestService ingest;

    private BoardEventProcessor boardEventProcessor;

    @BeforeEach
    void setUp() {
        when(builder.processMessage(any())).thenReturn(builder);
        when(builder.processError(any())).thenReturn(builder);
        when(builder.buildProcessorClient()).thenReturn(client);

        boardEventProcessor = new BoardEventProcessor(builder, PROPERTIES, ingest);
    }

    @Test
    @DisplayName("Given a processor client builder, when the processor is constructed, then message and error handlers are registered and the client is built")
    void constructorRegistersHandlers() {
        verify(builder).processMessage(any());
        verify(builder).processError(any());
        verify(builder).buildProcessorClient();
        assertThat(boardEventProcessor.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Given a received Service Bus message, when the message handler runs, then the message id, subject and body are handed to the ingest service")
    void messageHandlerDelegatesToIngestService() {
        ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        when(message.getMessageId()).thenReturn("100");
        when(message.getSubject()).thenReturn(TicketCreated.TYPE);
        when(message.getBody()).thenReturn(BinaryData.fromString("{\"ticketId\":100}"));
        ServiceBusReceivedMessageContext context = mock(ServiceBusReceivedMessageContext.class);
        when(context.getMessage()).thenReturn(message);

        messageHandler().accept(context);

        verify(ingest).ingest("100", TicketCreated.TYPE, "{\"ticketId\":100}");
    }

    @Test
    @DisplayName("Given an ingest failure, when the message handler runs, then the exception propagates to the Service Bus client")
    void messageHandlerPropagatesIngestFailure() {
        ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        when(message.getMessageId()).thenReturn("100");
        when(message.getSubject()).thenReturn(TicketCreated.TYPE);
        when(message.getBody()).thenReturn(BinaryData.fromString("not-json"));
        ServiceBusReceivedMessageContext context = mock(ServiceBusReceivedMessageContext.class);
        when(context.getMessage()).thenReturn(message);
        doThrow(new IllegalStateException("boom")).when(ingest).ingest(any(), any(), any());

        Consumer<ServiceBusReceivedMessageContext> handler = messageHandler();

        assertThatThrownBy(() -> handler.accept(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    @DisplayName("Given a Service Bus error, when the error handler runs, then the failure is logged without being rethrown")
    void errorHandlerSwallowsError() {
        ServiceBusErrorContext context = mock(ServiceBusErrorContext.class);
        when(context.getEntityPath()).thenReturn("board-events/subscriptions/notifications");
        when(context.getException()).thenReturn(new RuntimeException("connection lost"));

        Consumer<ServiceBusErrorContext> handler = errorHandler();

        assertThatCode(() -> handler.accept(context)).doesNotThrowAnyException();
        verifyNoInteractions(ingest);
    }

    @Test
    @DisplayName("Given a constructed processor, when start is called, then the client is started and the processor reports running")
    void startStartsClient() {
        boardEventProcessor.start();

        verify(client).start();
        assertThat(boardEventProcessor.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Given a running processor, when stop is called, then the client is closed and the processor no longer reports running")
    void stopClosesClient() {
        boardEventProcessor.start();

        boardEventProcessor.stop();

        verify(client).close();
        assertThat(boardEventProcessor.isRunning()).isFalse();
    }

    private Consumer<ServiceBusReceivedMessageContext> messageHandler() {
        ArgumentCaptor<Consumer<ServiceBusReceivedMessageContext>> captor = ArgumentCaptor.captor();
        verify(builder).processMessage(captor.capture());
        return captor.getValue();
    }

    private Consumer<ServiceBusErrorContext> errorHandler() {
        ArgumentCaptor<Consumer<ServiceBusErrorContext>> captor = ArgumentCaptor.captor();
        verify(builder).processError(captor.capture());
        return captor.getValue();
    }
}
