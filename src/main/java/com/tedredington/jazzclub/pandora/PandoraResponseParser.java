package com.tedredington.jazzclub.pandora;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tedredington.jazzclub.pandora.error.AudioQualityUnavailableException;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.error.PandoraProtocolException;
import com.tedredington.jazzclub.pandora.model.AudioEncoding;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns response bodies into domain objects. Unknown fields are ignored, optional ones defaulted. */
final class PandoraResponseParser {

    private static final int RATING_LOVED = 1;

    private final JsonMapper json;

    PandoraResponseParser(JsonMapper json) {
        this.json = json;
    }

    /** Unwraps the {@code {"stat": ..., "result": ...}} envelope, turning {@code "fail"} into an exception. */
    JsonNode result(String responseBody) {
        JsonNode root;
        try {
            root = json.readTree(responseBody);
        } catch (JacksonException e) {
            throw new PandoraProtocolException("Response is not valid JSON", e);
        }
        String status = root.path("stat").asString(null);
        if (status == null) {
            throw new PandoraProtocolException("Response has no status");
        }
        if (!"ok".equals(status)) {
            JsonNode code = root.path("code");
            if (!code.isIntegralNumber()) {
                throw new PandoraProtocolException("Failed response has no error code");
            }
            throw new PandoraApiException(code.asInt(), root.path("message").asString(null));
        }
        // some calls legitimately have no result
        return root.path("result");
    }

    String requiredText(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        if (value == null || value.isEmpty()) {
            throw new PandoraProtocolException("Response is missing '" + field + "'");
        }
        return value;
    }

    List<Station> stations(JsonNode result) {
        Set<String> quickMixMembers = new HashSet<>();
        for (JsonNode station : result.path("stations")) {
            for (JsonNode id : station.path("quickMixStationIds")) {
                quickMixMembers.add(id.asString());
            }
        }

        List<Station> stations = new ArrayList<>();
        for (JsonNode station : result.path("stations")) {
            String token = requiredText(station, "stationToken");
            stations.add(new Station(
                    token,
                    requiredText(station, "stationName"),
                    !station.path("isShared").asBoolean(false),
                    station.path("isQuickMix").asBoolean(false),
                    // Pandora lists members by stationId; pianobar compares them to the token, which is the same value
                    quickMixMembers.contains(token)));
        }
        return List.copyOf(stations);
    }

    List<Song> playlist(JsonNode result, AudioQuality quality) {
        List<Song> songs = new ArrayList<>();
        for (JsonNode item : result.path("items")) {
            // entries without an artist are ad tokens, not songs
            if (item.path("artistName").isMissingNode() || item.path("audioUrlMap").isMissingNode()) {
                continue;
            }
            JsonNode stream = item.path("audioUrlMap").path(quality.apiKey());
            String audioUrl = stream.path("audioUrl").asString(null);
            if (audioUrl == null) {
                throw new AudioQualityUnavailableException(quality);
            }
            songs.add(new Song(
                    item.path("songName").asString(null),
                    item.path("artistName").asString(null),
                    item.path("albumName").asString(null),
                    requiredText(item, "trackToken"),
                    item.path("stationId").asString(null),
                    URI.create(audioUrl),
                    AudioEncoding.fromApiValue(stream.path("encoding").asString("")),
                    optionalUri(item, "albumArtUrl"),
                    optionalUri(item, "songDetailUrl"),
                    parseGain(item.path("trackGain")),
                    Duration.ofSeconds(item.path("trackLength").asLong(0)),
                    item.path("songRating").asInt(0) == RATING_LOVED ? Rating.LOVE : Rating.NONE));
        }
        return List.copyOf(songs);
    }

    /** Joins the traits like pianobar: "a, b and c." */
    Optional<String> explanation(JsonNode result) {
        List<String> traits = new ArrayList<>();
        for (JsonNode explanation : result.path("explanations")) {
            String trait = explanation.path("focusTraitName").asString(null);
            if (trait != null && !trait.isBlank()) {
                traits.add(trait);
            }
        }
        if (traits.isEmpty()) {
            return Optional.empty();
        }
        String last = traits.removeLast();
        // Pandora's closing trait often reads "and many other similarities ..."; don't say "and and"
        String conjunction = last.startsWith("and ") ? ", " : " and ";
        String listed = traits.isEmpty() ? last : String.join(", ", traits) + conjunction + last;
        return Optional.of("We're playing this track because it features " + listed + ".");
    }

    /** Pandora sends the gain as a string ("-3.21"), json-c coerced it; so do we. */
    private static double parseGain(JsonNode gain) {
        if (gain.isNumber()) {
            return gain.asDouble();
        }
        try {
            return Double.parseDouble(gain.asString("0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static URI optionalUri(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
