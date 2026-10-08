package dev.karton.spotifywidget.config;

import dev.karton.spotifywidget.compat.Screens;
import dev.karton.spotifywidget.media.Media;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen, reachable from Mod Menu. Built out of plain vanilla widgets so the same code
 * works on every supported game version.
 */
public class ConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 150;
    private static final int COLUMN_GAP = 8;

    private static final float[] SCALES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final int[] OPACITIES = {40, 60, 75, 88, 100};
    private static final int[] OFFSETS = {0, 2, 4, 6, 8, 12, 16, 24, 32};
    /** Y also accepts negatives, which push the "above hotbar" line further down. */
    private static final int[] Y_OFFSETS = {0, 2, 4, 6, 8, 12, 16, 24, 32, -4, -8, -12};
    private static final int[] POLL_RATES = {500, 1000, 2000, 3000};

    private final Screen parent;
    private final HudConfig config = HudConfig.get();

    private StringWidget statusLabel;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Spotify Widget"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int left = centerX - BUTTON_WIDTH - COLUMN_GAP / 2;
        int right = centerX + COLUMN_GAP / 2;
        int top = 34;

        addRenderableWidget(new StringWidget(centerX - 100, 16, 200, 12,
                Component.literal("Spotify Widget"), this.font));

        int row = 0;

        addRow(left, right, top, row++,
                toggle("Widget", () -> config.enabled, value -> config.enabled = value),
                cycle(() -> "Design: " + config.layout.label(), () -> config.layout = config.layout.next()));

        addRow(left, right, top, row++,
                cycle(() -> "Display: " + config.displayMode.label(),
                        () -> config.displayMode = config.displayMode.next()),
                cycle(() -> "Corner: " + config.corner.label(), () -> config.corner = config.corner.next()));

        addRow(left, right, top, row++,
                toggle("Show at song start", () -> config.showOnSongStart, value -> config.showOnSongStart = value),
                toggle("Show at song end", () -> config.showOnSongEnd, value -> config.showOnSongEnd = value));

        addRow(left, right, top, row++,
                cycle(() -> "Scale: " + String.format("%.2fx", config.scale),
                        () -> config.scale = nextOf(SCALES, config.scale)),
                cycle(() -> "Background: " + config.backgroundOpacity + "%",
                        () -> config.backgroundOpacity = nextOf(OPACITIES, config.backgroundOpacity)));

        addRow(left, right, top, row++,
                cycle(() -> "Offset X: " + config.offsetX, () -> config.offsetX = nextOf(OFFSETS, config.offsetX)),
                cycle(() -> "Offset Y: " + config.offsetY, () -> config.offsetY = nextOf(Y_OFFSETS, config.offsetY)));

        addRow(left, right, top, row++,
                toggle("Album art", () -> config.showAlbumArt, value -> config.showAlbumArt = value),
                toggle("Timestamps", () -> config.showTimestamps, value -> config.showTimestamps = value));

        addRow(left, right, top, row++,
                toggle("Equaliser bars", () -> config.showWaveform, value -> config.showWaveform = value),
                toggle("Hide while a menu is open", () -> config.hideWithGui, value -> config.hideWithGui = value));

        addRow(left, right, top, row++,
                toggle("Jukebox in widget", () -> config.jukeboxWidget, value -> config.jukeboxWidget = value),
                toggle("Pause for jukebox", () -> config.pauseForJukebox, value -> config.pauseForJukebox = value));

        addRow(left, right, top, row++,
                cycle(() -> "Player: " + (config.spotifyAppOnly ? "Spotify only" : "Any player"),
                        () -> config.spotifyAppOnly = !config.spotifyAppOnly),
                cycle(() -> "Refresh: " + config.systemPollMillis + " ms",
                        () -> config.systemPollMillis = nextOf(POLL_RATES, config.systemPollMillis)));

        addRow(left, right, top, row++,
                toggle("Hide when paused", () -> config.hidePaused, value -> config.hidePaused = value),
                Button.builder(Component.literal("Reset to defaults"), button ->
                        Screens.open(new ConfirmScreen(confirmed -> {
                            if (confirmed) {
                                config.reset();
                                config.save();
                            }
                            Screens.open(new ConfigScreen(parent));
                        }, Component.literal("Reset all settings?"),
                                Component.literal("Every Spotify Widget option goes back to its default.")))));

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX - 75, top + row++ * ROW_HEIGHT, 150, 20)
                .build());

        int statusY = top + row * ROW_HEIGHT + 6;
        statusLabel = new StringWidget(centerX - 152, Math.min(this.height - 16, statusY), 304, 12,
                Component.literal(status()), this.font);
        addRenderableWidget(statusLabel);
    }

    private String status() {
        return "Now playing source: " + Media.status();
    }

    private void addRow(int left, int right, int top, int row, Button.Builder first, Button.Builder second) {
        int y = top + row * ROW_HEIGHT;
        addRenderableWidget(first.bounds(left, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(second.bounds(right, y, BUTTON_WIDTH, 20).build());
    }

    private Button.Builder toggle(String label, java.util.function.BooleanSupplier getter,
                                  java.util.function.Consumer<Boolean> setter) {
        return Button.builder(Component.literal(label + ": " + onOff(getter.getAsBoolean())), button -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            button.setMessage(Component.literal(label + ": " + onOff(value)));
        });
    }

    private Button.Builder cycle(java.util.function.Supplier<String> label, Runnable action) {
        return Button.builder(Component.literal(label.get()), button -> {
            action.run();
            button.setMessage(Component.literal(label.get()));
        });
    }

    @Override
    public void tick() {
        super.tick();
        // The player is read on a background thread, so keep the status line current
        if (statusLabel != null) statusLabel.setMessage(Component.literal(status()));
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static float nextOf(float[] values, float current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.001f) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static int nextOf(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    @Override
    public void onClose() {
        config.save();
        Screens.open(parent);
    }
}
