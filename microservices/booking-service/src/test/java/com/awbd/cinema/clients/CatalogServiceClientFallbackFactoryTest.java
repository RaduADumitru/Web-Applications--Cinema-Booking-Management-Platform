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

class CatalogServiceClientFallbackFactoryTest {

    private CatalogServiceClientFallbackFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CatalogServiceClientFallbackFactory(new FeignClientErrorTranslator(new ObjectMapper()));
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

        assertThatThrownBy(() -> factory.create(cause).getTicketSetup(1L, 2L, 3L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Screen session not found.");
    }

    @Test
    void propagatesBadRequest_whenCatalogReturns400_withRealMessage() {
        Throwable cause = feign(400, "{\"status\":400,\"message\":\"Seat does not belong to the specified room.\"}");

        assertThatThrownBy(() -> factory.create(cause).getTicketSetup(1L, 2L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Seat does not belong to the specified room.");
    }

    @Test
    void propagatesConflict_whenCatalogReturns409() {
        Throwable cause = feign(409, "{\"message\":\"Already exists.\"}");

        assertThatThrownBy(() -> factory.create(cause).getTicketSetup(1L, 2L, 3L))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Already exists.");
    }

    @Test
    void usesDefaultMessage_whenClientErrorBodyIsUnparseable() {
        Throwable cause = feign(404, "<html>not json</html>");

        assertThatThrownBy(() -> factory.create(cause).getTicketSetup(1L, 2L, 3L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("The request could not be completed.");
    }

    @Test
    void fallsBackToUnavailable_whenCatalogReturns5xx() {
        Throwable cause = feign(503, "{\"message\":\"boom\"}");

        assertThatThrownBy(() -> factory.create(cause).getTicketSetup(1L, 2L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }

    @Test
    void fallsBackToUnavailable_whenCatalogIsUnreachable() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        assertThatThrownBy(() -> factory.create(cause).getTicketSetup(1L, 2L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }
}
