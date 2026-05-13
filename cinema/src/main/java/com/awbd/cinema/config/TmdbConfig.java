package com.awbd.cinema.config;

import info.movito.themoviedbapi.TmdbApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TmdbConfig {

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    @Bean
    public TmdbApi tmdbApi() {
        return new TmdbApi(tmdbApiKey);
    }
}
