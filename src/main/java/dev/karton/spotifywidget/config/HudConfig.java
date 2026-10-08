package dev.karton.spotifywidget.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.karton.spotifywidget.SpotifyWidgetClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod settings, stored in {@code config/spotifywidget.json}.
 * Read and written by hand so an older config file never throws away unrelated keys.
 */
public final class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HudConfig instance;

    public boolean enabled = true;
    /** Only read the Spotify player, ignore browsers and other media apps. */
    public boolean spotifyAppOnly = true;
    /** How often the player is read, in milliseconds. */
    public int systemPollMillis = 1000;
    /** Show a playing jukebox record in the widget. */
    public boolean jukeboxWidget = true;
    /** Pause the desktop player while a jukebox record plays, and resume it afterwards. */
    public boolean pauseForJukebox = true;

    /** Which of the widget designs to draw. */
    public WidgetLayout layout = WidgetLayout.CLASSIC;
    public DisplayMode displayMode = DisplayMode.ALWAYS;
    /** Timed modes only: pop up when a track starts. */
    public boolean showOnSongStart = true;
    /** Timed modes only: pop up again for the last seconds of a track. */
    public boolean showOnSongEnd = true;

    public HudCorner corner = HudCorner.TOP_LEFT;
    public int offsetX = 6;
    public int offsetY = 6;
    public float scale = 1.0f;
    public int backgroundOpacity = 88; // percent
    public boolean showAlbumArt = true;
    public boolean showTimestamps = true;
    public boolean showWaveform = true;
    /** Keep the widget off screen while any GUI (inventory, chat, menus) is open. */
    public boolean hideWithGui = false;
    /** Fade the widget out while the player is paused. */
    public boolean hidePaused = false;

    public static HudConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Puts every setting back to its default, in place so existing references stay valid. */
    public void reset() {
        HudConfig defaults = new HudConfig();
        try {
            for (java.lang.reflect.Field field : HudConfig.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(this, field.get(defaults));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotifywidget.json");
    }

    public static HudConfig load() {
        HudConfig config = new HudConfig();
        Path path = path();
        if (!Files.exists(path)) {
            config.save();
            return config;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            config.enabled = bool(json, "enabled", config.enabled);
            config.spotifyAppOnly = bool(json, "spotifyAppOnly", config.spotifyAppOnly);
            config.systemPollMillis = Math.max(250, integer(json, "systemPollMillis", config.systemPollMillis));
            config.jukeboxWidget = bool(json, "jukeboxWidget", config.jukeboxWidget);
            config.pauseForJukebox = bool(json, "pauseForJukebox", config.pauseForJukebox);
            config.layout = WidgetLayout.byId(string(json, "layout", config.layout.id()), config.layout);
            config.displayMode = DisplayMode.byId(string(json, "displayMode", config.displayMode.id()), config.displayMode);
            config.showOnSongStart = bool(json, "showOnSongStart", config.showOnSongStart);
            config.showOnSongEnd = bool(json, "showOnSongEnd", config.showOnSongEnd);
            config.corner = HudCorner.byId(string(json, "corner", config.corner.id()), config.corner);
            config.offsetX = integer(json, "offsetX", config.offsetX);
            config.offsetY = integer(json, "offsetY", config.offsetY);
            config.scale = (float) number(json, "scale", config.scale);
            config.backgroundOpacity = integer(json, "backgroundOpacity", config.backgroundOpacity);
            config.showAlbumArt = bool(json, "showAlbumArt", config.showAlbumArt);
            config.showTimestamps = bool(json, "showTimestamps", config.showTimestamps);
            config.showWaveform = bool(json, "showWaveform", config.showWaveform);
            config.hideWithGui = bool(json, "hideWithGui", config.hideWithGui);
            config.hidePaused = bool(json, "hidePaused", config.hidePaused);
        } catch (Exception e) {
            SpotifyWidgetClient.LOGGER.warn("Could not read spotifywidget.json, using defaults", e);
        }
        return config;
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("spotifyAppOnly", spotifyAppOnly);
        json.addProperty("systemPollMillis", systemPollMillis);
        json.addProperty("jukeboxWidget", jukeboxWidget);
        json.addProperty("pauseForJukebox", pauseForJukebox);
        json.addProperty("layout", layout.id());
        json.addProperty("displayMode", displayMode.id());
        json.addProperty("showOnSongStart", showOnSongStart);
        json.addProperty("showOnSongEnd", showOnSongEnd);
        json.addProperty("corner", corner.id());
        json.addProperty("offsetX", offsetX);
        json.addProperty("offsetY", offsetY);
        json.addProperty("scale", scale);
        json.addProperty("backgroundOpacity", backgroundOpacity);
        json.addProperty("showAlbumArt", showAlbumArt);
        json.addProperty("showTimestamps", showTimestamps);
        json.addProperty("showWaveform", showWaveform);
        json.addProperty("hideWithGui", hideWithGui);
        json.addProperty("hidePaused", hidePaused);
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SpotifyWidgetClient.LOGGER.error("Could not write spotifywidget.json", e);
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }
}
