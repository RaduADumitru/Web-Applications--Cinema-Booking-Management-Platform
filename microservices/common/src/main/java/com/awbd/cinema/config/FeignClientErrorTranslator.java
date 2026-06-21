package com.awbd.cinema.config;

import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared by every Feign client gateway's fallback method so they handle downstream failures consistently:
 *
 * <ul>
 *   <li>A genuine 4xx from the downstream (e.g. "not found", "bad request") is a <em>business</em>
 *       error — {@link #clientErrorOrNull(Throwable)} maps it to the matching domain exception
 *       (carrying the downstream's message) so the caller can surface it with its real status
 *       instead of masking it as an outage or silently degrading.</li>
 *   <li>Anything else (5xx, timeouts, connection errors, an open circuit) is a genuine availability
 *       failure: this returns {@code null}, and the caller applies its own fallback behaviour
 *       (fail with "unavailable", degrade to a default, skip a non-critical call, …).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FeignClientErrorTranslator {

    private final ObjectMapper objectMapper;

    /** The matching domain exception if {@code cause} is a 4xx Feign error, else {@code null}. */
    public RuntimeException clientErrorOrNull(Throwable cause) {
        FeignException fe = clientError(cause);
        return fe == null ? null : translate(fe);
    }

    private static FeignException clientError(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
                return fe;
            }
        }
        return null;
    }

    private RuntimeException translate(FeignException fe) {
        String message = extractMessage(fe, "The request could not be completed.");
        return switch (fe.status()) {
            case 404 -> new NotFoundException(message);
            case 409 -> new AlreadyExistsException(message);
            case 422 -> new InvalidFieldException(message);
            case 429 -> new TooManyRequestsException(message);
            default -> new BadRequestException(message);
        };
    }

    private String extractMessage(FeignException fe, String fallback) {
        try {
            String body = fe.contentUTF8();
            if (body != null && !body.isBlank()) {
                JsonNode message = objectMapper.readTree(body).get("message");
                if (message != null && !message.isNull() && !message.asText().isBlank()) {
                    return message.asText();
                }
            }
        } catch (Exception ignored) {
            // fall through to the default message
        }
        return fallback;
    }
}
