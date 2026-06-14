package com.awbd.cinema.config;

import info.movito.themoviedbapi.TmdbApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbConfigTest {

    private TmdbConfig tmdbConfig;
    private final String dummyApiKey = "fake_tmdb_api_key_12345";

    @BeforeEach
    void setUp() {
        tmdbConfig = new TmdbConfig();

        ReflectionTestUtils.setField(tmdbConfig, "tmdbApiKey", dummyApiKey);
    }

    @Test
    void tmdbApi_ShouldReturnConfiguredInstance() {
        TmdbApi tmdbApi = tmdbConfig.tmdbApi();

        assertThat(tmdbApi).isNotNull();
    }
}