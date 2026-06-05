package com.awbd.cinema.enums;

import java.util.Map;
import java.util.Optional;

public enum GenreType {
    ACTION, ADVENTURE, ANIMATION, COMEDY, CRIME, DOCUMENTARY,
    DRAMA, FAMILY, FANTASY, HISTORY, HORROR, MUSIC, MYSTERY,
    ROMANCE, SCI_FI, THRILLER, TV_MOVIE, WAR, WESTERN;

    private static final Map<Integer, GenreType> TMDB_ID_MAP = Map.ofEntries(
            Map.entry(28,    ACTION),
            Map.entry(12,    ADVENTURE),
            Map.entry(16,    ANIMATION),
            Map.entry(35,    COMEDY),
            Map.entry(80,    CRIME),
            Map.entry(99,    DOCUMENTARY),
            Map.entry(18,    DRAMA),
            Map.entry(10751, FAMILY),
            Map.entry(14,    FANTASY),
            Map.entry(36,    HISTORY),
            Map.entry(27,    HORROR),
            Map.entry(10402, MUSIC),
            Map.entry(9648,  MYSTERY),
            Map.entry(10749, ROMANCE),
            Map.entry(878,   SCI_FI),
            Map.entry(53,    THRILLER),
            Map.entry(10770, TV_MOVIE),
            Map.entry(10752, WAR),
            Map.entry(37,    WESTERN)
    );

    public static Optional<GenreType> fromTmdbId(int tmdbId) {
        return Optional.ofNullable(TMDB_ID_MAP.get(tmdbId));
    }
}