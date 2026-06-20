package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.config.server.native.search-locations=classpath:/test-config",
                "encrypt.key=test-symmetric-encrypt-key-1234567890",
                "management.endpoints.web.exposure.include=*"
        }
)
class EncryptionEndpointTest {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        String plaintext = "super-secret-value";

        String cipher = client().post().uri("/encrypt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(plaintext)
                .retrieve()
                .body(String.class);
        assertThat(cipher).isNotBlank().isNotEqualTo(plaintext);

        String decrypted = client().post().uri("/decrypt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(cipher)
                .retrieve()
                .body(String.class);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void servesNativeConfigAndPassesPlaceholdersThrough() {
        String body = client().get().uri("/demo-app/default")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("a-plain-value");
        // The JSON Environment endpoint must NOT resolve placeholders: clients do.
        assertThat(body).contains("${DEMO_ENV_VALUE}");
    }
}
