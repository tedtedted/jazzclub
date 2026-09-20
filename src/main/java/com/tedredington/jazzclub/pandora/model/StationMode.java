package com.tedredington.jazzclub.pandora.model;

/** One of Pandora's station modes, e.g. "Crowd Faves", "Discovery", "Deep Cuts". */
public record StationMode(int id, String name, String description, boolean active) {
}
