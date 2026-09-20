package com.tedredington.jazzclub.pandora.model;

import java.util.List;

/** A group of Pandora's ready-made genre stations, e.g. "Jazz". */
public record GenreCategory(String name, List<Genre> genres) {

    public GenreCategory {
        genres = List.copyOf(genres);
    }

    public record Genre(String name, String musicToken) {
    }
}
