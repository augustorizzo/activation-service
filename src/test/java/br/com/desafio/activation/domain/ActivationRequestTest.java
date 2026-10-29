package br.com.desafio.activation.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivationRequestTest {

    @Test
    void shouldStartAsPending() {
        var request = newRequest();

        assertEquals(ActivationStatus.PENDING, request.getStatus());
    }

    @Test
    void shouldMoveFromPendingToProcessing() {
        var request = newRequest();

        request.startProcessing();

        assertEquals(ActivationStatus.PROCESSING, request.getStatus());
    }

    @Test
    void shouldCompleteProcessingRequest() {
        var request = newRequest();
        request.startProcessing();

        request.complete();

        assertEquals(ActivationStatus.COMPLETED, request.getStatus());
    }

    @Test
    void shouldFailProcessingRequest() {
        var request = newRequest();
        request.startProcessing();

        request.fail();

        assertEquals(ActivationStatus.FAILED, request.getStatus());
    }

    @Test
    void shouldRejectCompletionBeforeProcessing() {
        var request = newRequest();

        assertThrows(IllegalStateException.class, request::complete);

        assertEquals(ActivationStatus.PENDING, request.getStatus());
    }

    @Test
    void shouldRejectFailureBeforeProcessing() {
        var request = newRequest();

        assertThrows(IllegalStateException.class, request::fail);

        assertEquals(ActivationStatus.PENDING, request.getStatus());
    }

    @Test
    void shouldRejectStartingProcessingTwice() {
        var request = newRequest();
        request.startProcessing();

        assertThrows(
                IllegalStateException.class,
                request::startProcessing
        );

        assertEquals(ActivationStatus.PROCESSING, request.getStatus());
    }

    @Test
    void shouldRejectReprocessingCompletedRequest() {
        var request = newRequest();
        request.startProcessing();
        request.complete();

        assertThrows(
                IllegalStateException.class,
                request::startProcessing
        );

        assertEquals(ActivationStatus.COMPLETED, request.getStatus());
    }

    @Test
    void shouldRejectChangingCompletedRequestToFailed() {
        var request = newRequest();
        request.startProcessing();
        request.complete();

        assertThrows(IllegalStateException.class, request::fail);

        assertEquals(ActivationStatus.COMPLETED, request.getStatus());
    }

    @Test
    void shouldRejectReprocessingFailedRequest() {
        var request = newRequest();
        request.startProcessing();
        request.fail();

        assertThrows(
                IllegalStateException.class,
                request::startProcessing
        );

        assertEquals(ActivationStatus.FAILED, request.getStatus());
    }

    @Test
    void shouldRejectChangingFailedRequestToCompleted() {
        var request = newRequest();
        request.startProcessing();
        request.fail();

        assertThrows(IllegalStateException.class, request::complete);

        assertEquals(ActivationStatus.FAILED, request.getStatus());
    }

    private ActivationRequest newRequest() {
        return new ActivationRequest(
                "EXT-DOMAIN-001",
                "CUST-10001",
                "INTERNET-500"
        );
    }
}