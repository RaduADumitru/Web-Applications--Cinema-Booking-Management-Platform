package com.awbd.cinema.clients;

import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceGatewayTest {

    private CatalogServiceGateway gateway;

    @BeforeEach
    void setUp() {
        // The raw client is unused by the fallback path, so a bare mock is fine.
        gateway = new CatalogServiceGateway(
                mock(CatalogServiceClient.class),
                new FeignClientErrorTranslator(new ObjectMapper()));
    }

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void propagatesNotFound_whenCatalogReturns404_withRealMessage() {
        Throwable cause = feign(404, "{\"status\":404,\"message\":\"Screen session not found.\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Screen session not found.");
    }

    @Test
    void propagatesConflict_whenCatalogReturns409() {
        Throwable cause = feign(409, "{\"message\":\"Already exists.\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Already exists.");
    }

    @Test
    void usesDefaultMessage_whenClientErrorBodyIsUnparseable() {
        Throwable cause = feign(404, "<html>not json</html>");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("The request could not be completed.");
    }

    @Test
    void fallsBackToUnavailable_whenCatalogReturns5xx() {
        Throwable cause = feign(503, "{\"message\":\"boom\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }

    @Test
    void fallsBackToUnavailable_whenCatalogIsUnreachable() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }

    @Test
    void bulk_propagatesClientError_whenCatalogReturns404() {
        Throwable cause = feign(404, "{\"message\":\"Screen session not found.\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupsFallback(null, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Screen session not found.");
    }

    @Test
    void bulk_fallsBackToUnavailable_onOutage() {
        Throwable cause = new RuntimeException("Connection refused");

        assertThatThrownBy(() -> gateway.getTicketSetupsFallback(null, cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }
}
