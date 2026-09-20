package com.tedredington.jazzclub.pandora.model;

/** The part of the listener's Pandora account that can be viewed and changed from here. */
public record AccountSettings(String username, boolean explicitContentFilter) {
}
