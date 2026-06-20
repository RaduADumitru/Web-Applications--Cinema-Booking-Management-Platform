package com.awbd.cinema.config;

import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignClientErrorTranslatorTest {

    private final FeignClientErrorTranslator translator = new FeignClientErrorTranslator(new ObjectMapper());

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void maps404ToNotFound_withDownstreamMessage() {
        RuntimeException ex = translator.clientErrorOrNull(
                feign(404, "{\"status\":404,\"message\":\"Screen session not found.\"}"));

        assertThat(ex).isInstanceOf(NotFoundException.class).hasMessage("Screen session not found.");
    }

    @Test
    void mapsStatusesToTheMatchingDomainException() {
        assertThat(translator.clientErrorOrNull(feign(400, "{\"message\":\"bad\"}")))
                .isInstanceOf(BadRequestException.class).hasMessage("bad");
        assertThat(translator.clientErrorOrNull(feign(409, "{\"message\":\"dup\"}")))
                .isInstanceOf(AlreadyExistsException.class).hasMessage("dup");
        assertThat(translator.clientErrorOrNull(feign(422, "{\"message\":\"invalid\"}")))
                .isInstanceOf(InvalidFieldException.class).hasMessage("invalid");
        assertThat(translator.clientErrorOrNull(feign(429, "{\"message\":\"slow down\"}")))
                .isInstanceOf(TooManyRequestsException.class).hasMessage("slow down");
    }

    @Test
    void usesDefaultMessage_whenBodyIsNotParseableJson() {
        RuntimeException ex = translator.clientErrorOrNull(feign(404, "<html>nope</html>"));

        assertThat(ex).isInstanceOf(NotFoundException.class).hasMessage("The request could not be completed.");
    }

    @Test
    void returnsNull_forOutages_soCallerAppliesItsOwnFallback() {
        assertThat(translator.clientErrorOrNull(feign(503, "{\"message\":\"boom\"}"))).isNull(); // 5xx
        assertThat(translator.clientErrorOrNull(mock(FeignException.class))).isNull();           // status 0 / generic
        assertThat(translator.clientErrorOrNull(new RuntimeException("connection refused"))).isNull();
    }
}
