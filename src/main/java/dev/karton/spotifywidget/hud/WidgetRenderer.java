package dev.karton.spotifywidget.hud;

import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.config.DisplayMode;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.config.HudCorner;
import dev.karton.spotifywidget.game.JukeboxWatcher;
import dev.karton.spotifywidget.media.Media;
import dev.karton.spotifywidget.media.Track;
import net.minecraft.resources.Identifier;

/** Draws the now-playing widget in whichever layout the config asks for. */
public final class WidgetRenderer {
    private static final int PADDING = 5;
    private static final int COVER = 32;
    private static final int GAP = 7;
    private static final int LINE = 11;
    private static final int CORNER = 8;
    private static final int MAX_TEXT_WIDTH = 140;
    /** How far the "above hotbar" line and the vanilla item name are both pushed down. */
    public static final int HOTBAR_DROP = 8;

    private static final int BAR_COUNT = 5;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_GAP = 2;
    private static final int BARS_WIDTH = BAR_COUNT * BAR_WIDTH + (BAR_COUNT - 1) * BAR_GAP;

    private static final int COLOR_PANEL = 0x14141A;
    private static final int COLOR_EDGE = 0x33343A;
    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_ARTIST = 0xB3B3B3;
    private static final int COLOR_TIME = 0x8C8C8C;
    private static final int COLOR_BARS = 0xDCDCDC;
    private static final int COLOR_TRACK = 0x3A3A44;
    private static final int COLOR_PROGRESS = 0x1DB954;

    // Vanilla tooltip palette, straight out of TooltipRenderUtil
    private static final int VANILLA_BACKGROUND = 0xF0100010;
    private static final int VANILLA_BORDER_TOP = 0x505000FF;
    private static final int VANILLA_BORDER_BOTTOM = 0x5028007F;
    private static final int VANILLA_WHITE = 0xFFFFFF;
    private static final int VANILLA_GRAY = 0xAAAAAA;
    private static final int VANILLA_DARK_GRAY = 0x555555;
    private static final int VANILLA_LINE = 10;

    private static float alpha;
    private static long lastFrameNanos;
    private static String lastTrackId;
    private static long trackChangedAt;

    private WidgetRenderer() {
    }

    public static void render(Canvas canvas) {
        HudConfig config = HudConfig.get();
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        Track jukebox = config.jukeboxWidget ? JukeboxWatcher.track() : null;
        Track track = jukebox != null ? jukebox : Media.track();
        if (track != null && !track.id.equals(lastTrackId)) {
            lastTrackId = track.id;
            trackChangedAt = now;
        }

        boolean wanted = config.enabled && shouldShow(config, track, now);
        float target = wanted ? 1f : 0f;
        float speed = wanted ? 4.5f : 3.0f;
        alpha += (target - alpha) * Math.min(1f, delta * speed);
        if (alpha < 0.01f) {
            alpha = 0f;
            return;
        }
        if (track == null) return;

        switch (config.layout) {
            case BAR -> drawBar(canvas, config, track, alpha, now);
            case COMPACT -> drawCompact(canvas, config, track, alpha);
            case HOTBAR -> drawHotbarLine(canvas, config, track, alpha, now);
            case VANILLA -> drawVanilla(canvas, config, track, alpha, now);
            default -> drawClassic(canvas, config, track, alpha, now);
        }
    }

    private static boolean shouldShow(HudConfig config, Track track, long now) {
        if (track == null) return false;
        if (config.hideWithGui && Canvas.guiOpen()) return false;
        if (config.hidePaused && !track.playing) return false;
        DisplayMode mode = config.displayMode;
        if (!mode.timed()) return true;

        long window = mode.seconds() * 1000L;
        if (config.showOnSongStart) {
            if (track.currentProgressMs() <= window) return true;
            // Also cover the case where playback jumps into a track that is already past its intro
            if ((now - trackChangedAt) / 1_000_000L <= window) return true;
        }
        if (config.showOnSongEnd && track.durationMs > 0 && track.remainingMs() <= window) return true;
        return false;
    }

    // --- layouts ---------------------------------------------------------------

    /** Album art, title, artist, elapsed/total and the equaliser. */
    private static void drawClassic(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        String title = canvas.trim(text(track.title), MAX_TEXT_WIDTH);
        String artist = canvas.trim(text(track.artist), MAX_TEXT_WIDTH);
        String time = timeLabel(track);

        int textWidth = Math.max(canvas.textWidth(title), canvas.textWidth(artist));
        if (config.showTimestamps) textWidth = Math.max(textWidth, canvas.textWidth(time));
        textWidth = Math.min(textWidth, MAX_TEXT_WIDTH);

        int width = PADDING * 2 + textWidth;
        if (config.showAlbumArt) width += COVER + GAP;
        if (config.showWaveform) width += BARS_WIDTH + GAP;
        int height = PADDING * 2 + COVER;

        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];

        panel(canvas, config, x, y, width, height, alpha);

        int contentX = x + PADDING;
        if (config.showAlbumArt) contentX = cover(canvas, track, contentX, y + PADDING, COVER, alpha);

        int textAlpha = Math.round(255 * alpha);
        int textY = y + PADDING + 1;
        canvas.text(title, contentX, textY, argb(COLOR_TITLE, textAlpha), true);
        canvas.text(artist, contentX, textY + LINE, argb(COLOR_ARTIST, textAlpha), true);
        if (config.showTimestamps) {
            canvas.text(time, contentX, textY + LINE * 2, argb(COLOR_TIME, textAlpha), true);
        }
        if (config.showWaveform) {
            equaliser(canvas, x + width - PADDING - BARS_WIDTH, y + height / 2, track, alpha, now);
        }
        canvas.pop();
    }

    /** Album art and two lines of text, with a progress bar along the bottom edge. */
    private static void drawBar(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        String title = canvas.trim(text(track.title), MAX_TEXT_WIDTH);
        String time = timeLabel(track);
        int timeWidth = config.showTimestamps ? canvas.textWidth(time) + GAP : 0;
        String artist = canvas.trim(text(track.artist), Math.max(40, MAX_TEXT_WIDTH - timeWidth));

        int secondLine = canvas.textWidth(artist) + timeWidth;
        int textWidth = Math.min(MAX_TEXT_WIDTH, Math.max(canvas.textWidth(title), Math.max(secondLine, 80)));

        int width = PADDING * 2 + textWidth;
        if (config.showAlbumArt) width += COVER + GAP;
        if (config.showWaveform) width += BARS_WIDTH + GAP;
        int height = PADDING * 2 + COVER;

        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];

        panel(canvas, config, x, y, width, height, alpha);

        int contentX = x + PADDING;
        if (config.showAlbumArt) contentX = cover(canvas, track, contentX, y + PADDING, COVER, alpha);

        int textAlpha = Math.round(255 * alpha);
        canvas.text(title, contentX, y + PADDING + 2, argb(COLOR_TITLE, textAlpha), true);
        canvas.text(artist, contentX, y + PADDING + 2 + LINE, argb(COLOR_ARTIST, textAlpha), true);
        if (config.showTimestamps) {
            int timeX = x + width - PADDING - canvas.textWidth(time);
            if (config.showWaveform) timeX -= BARS_WIDTH + GAP;
            canvas.text(time, timeX, y + PADDING + 2 + LINE, argb(COLOR_TIME, textAlpha), true);
        }
        if (config.showWaveform) {
            equaliser(canvas, x + width - PADDING - BARS_WIDTH, y + PADDING + 8, track, alpha, now);
        }

        progress(canvas, contentX, y + height - PADDING - 4,
                x + width - PADDING - contentX, 4, track, alpha);
        canvas.pop();
    }

    /** One slim row: small album art, title, artist and a thin progress line. */
    private static void drawCompact(Canvas canvas, HudConfig config, Track track, float alpha) {
        final int art = 16;
        final int pad = 3;
        final int height = 22;
        final String dot = "·";

        String title = canvas.trim(text(track.title), 120);
        String artist = canvas.trim(text(track.artist), 90);
        String remaining = "-" + Track.formatTime(track.remainingMs());

        int textWidth = canvas.textWidth(title);
        if (!artist.isEmpty()) textWidth += 5 + canvas.textWidth(dot) + 5 + canvas.textWidth(artist);
        int timeWidth = config.showTimestamps ? 8 + canvas.textWidth(remaining) : 0;

        int width = pad * 2 + textWidth + timeWidth;
        if (config.showAlbumArt) width += art + 5;

        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];

        panel(canvas, config, x, y, width, height, alpha);

        int contentX = x + pad;
        if (config.showAlbumArt) {
            artwork(canvas, track, contentX, y + pad, art, alpha);
            contentX += art + 5;
        }

        int textAlpha = Math.round(255 * alpha);
        int textY = y + 7;
        int titleColor = track.playing ? COLOR_TITLE : 0xC6C6CC;
        canvas.text(title, contentX, textY, argb(titleColor, textAlpha), true);
        int after = contentX + canvas.textWidth(title);
        if (!artist.isEmpty()) {
            canvas.text(dot, after + 5, textY, argb(0x63636F, textAlpha), true);
            canvas.text(artist, after + 5 + canvas.textWidth(dot) + 5, textY, argb(COLOR_ARTIST, textAlpha), true);
        }
        if (config.showTimestamps) {
            canvas.text(remaining, x + width - pad - canvas.textWidth(remaining), textY,
                    argb(COLOR_TIME, textAlpha), true);
        }

        progress(canvas, x + pad, y + height - pad - 1, width - pad * 2, 2, track, alpha);
        canvas.pop();
    }

    /**
     * No panel at all: a centred line of text where vanilla shows the held item name, which sits
     * above the experience bar and status bars. Corner is ignored, the offset lifts it further up.
     */
    private static void drawHotbarLine(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final String dot = "·";
        String title = canvas.trim(text(track.title), 200);
        String artist = canvas.trim(text(track.artist), 160);
        String time = timeLabel(track);

        int titleWidth = canvas.textWidth(title);
        int dotWidth = canvas.textWidth(dot);
        int artistWidth = artist.isEmpty() ? 0 : 5 + dotWidth + 5 + canvas.textWidth(artist);
        int timeWidth = config.showTimestamps ? 8 + canvas.textWidth(time) : 0;
        int width = titleWidth + artistWidth + timeWidth;
        int barsWidth = config.showWaveform ? BARS_WIDTH + 6 : 0;

        float scale = Math.max(0.5f, Math.min(2.0f, config.scale));
        canvas.push(scale);
        int screenWidth = Math.round(canvas.screenWidth() / scale);
        int screenHeight = Math.round(canvas.screenHeight() / scale);

        int x = (screenWidth - (width + barsWidth)) / 2;
        // Vanilla draws the held item name at guiHeight - 59, or 14 lower in creative and spectator
        // where the health and experience rows are gone. Sit one line above whichever it is, so the
        // two never print on top of each other when you switch items.
        int base = (Canvas.statusBarsVisible() ? 69 : 55) - HOTBAR_DROP;
        int y = screenHeight - base - config.offsetY + Math.round((1f - alpha) * 4f);

        int textAlpha = Math.round(255 * alpha);
        if (config.showWaveform) {
            equaliser(canvas, x, y + 4, track, alpha, now);
            x += barsWidth;
        }
        int titleColor = track.playing ? COLOR_TITLE : 0xC6C6CC;
        canvas.text(title, x, y, argb(titleColor, textAlpha), true);
        int after = x + titleWidth;
        if (!artist.isEmpty()) {
            canvas.text(dot, after + 5, y, argb(0x8A8A96, textAlpha), true);
            canvas.text(artist, after + 5 + dotWidth + 5, y, argb(COLOR_ARTIST, textAlpha), true);
        }
        if (config.showTimestamps) {
            canvas.text(time, x + width - canvas.textWidth(time), y, argb(COLOR_TIME, textAlpha), true);
        }
        canvas.pop();
    }

    /**
     * Vanilla tooltip look: the same dark box, purple gradient border and text colours the game
     * uses for item tooltips, with the album art sitting where an item icon would.
     */
    private static void drawVanilla(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final int icon = 16;
        String title = canvas.trim(text(track.title), MAX_TEXT_WIDTH);
        String artist = canvas.trim(text(track.artist), MAX_TEXT_WIDTH);
        String time = timeLabel(track);

        int lines = 2 + (config.showTimestamps ? 1 : 0);
        int textWidth = Math.max(canvas.textWidth(title), canvas.textWidth(artist));
        if (config.showTimestamps) textWidth = Math.max(textWidth, canvas.textWidth(time));

        int contentWidth = textWidth;
        if (config.showAlbumArt) contentWidth += icon + 4;
        if (config.showWaveform) contentWidth += BARS_WIDTH + 6;
        int contentHeight = Math.max(lines * VANILLA_LINE - 2, config.showAlbumArt ? icon : 0);

        int width = contentWidth + 8;
        int height = contentHeight + 8;

        int[] origin = begin(canvas, config, width, height, alpha);
        int contentX = origin[0] + 4;
        int contentY = origin[1] + 4;

        tooltipFrame(canvas, contentX, contentY, contentWidth, contentHeight, alpha);

        int textX = contentX;
        if (config.showAlbumArt) {
            artwork(canvas, track, contentX, contentY, icon, alpha);
            textX += icon + 4;
        }

        int textAlpha = Math.round(255 * alpha);
        canvas.text(title, textX, contentY, argb(VANILLA_WHITE, textAlpha), true);
        canvas.text(artist, textX, contentY + VANILLA_LINE, argb(VANILLA_GRAY, textAlpha), true);
        if (config.showTimestamps) {
            canvas.text(time, textX, contentY + VANILLA_LINE * 2, argb(VANILLA_DARK_GRAY, textAlpha), true);
        }
        if (config.showWaveform) {
            equaliser(canvas, contentX + contentWidth - BARS_WIDTH, contentY + contentHeight / 2, track, alpha, now);
        }
        canvas.pop();
    }

    /** Same geometry as {@code TooltipRenderUtil}, drawn with flat rows so it works on every version. */
    private static void tooltipFrame(Canvas canvas, int x, int y, int width, int height, float alpha) {
        int background = scaleAlpha(VANILLA_BACKGROUND, alpha);
        canvas.rect(x - 3, y - 4, width + 6, 1, background);
        canvas.rect(x - 3, y + height + 3, width + 6, 1, background);
        canvas.rect(x - 3, y - 3, width + 6, height + 6, background);
        canvas.rect(x - 4, y - 3, 1, height + 6, background);
        canvas.rect(x + width + 3, y - 3, 1, height + 6, background);

        int top = scaleAlpha(VANILLA_BORDER_TOP, alpha);
        int bottom = scaleAlpha(VANILLA_BORDER_BOTTOM, alpha);
        int borderHeight = height + 4;
        for (int row = 0; row < borderHeight; row++) {
            int color = lerpColor(top, bottom, borderHeight <= 1 ? 0f : row / (float) (borderHeight - 1));
            canvas.rect(x - 3, y - 2 + row, 1, 1, color);
            canvas.rect(x + width + 2, y - 2 + row, 1, 1, color);
        }
        canvas.rect(x - 3, y - 3, width + 6, 1, top);
        canvas.rect(x - 3, y + height + 2, width + 6, 1, bottom);
    }

    private static int scaleAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        int color = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            color |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
        }
        return color;
    }

    // --- shared pieces ---------------------------------------------------------

    /** Works out the anchored position, applies the scale and returns the panel origin. */
    private static int[] begin(Canvas canvas, HudConfig config, int width, int height, float alpha) {
        float scale = Math.max(0.5f, Math.min(2.0f, config.scale));
        HudCorner corner = config.corner;
        int screenWidth = Math.round(canvas.screenWidth() / scale);
        int screenHeight = Math.round(canvas.screenHeight() / scale);
        int x = corner.left() ? config.offsetX : screenWidth - width - config.offsetX;
        int y = corner.top() ? config.offsetY : screenHeight - height - config.offsetY;

        // Slide in from the anchored edge
        int slide = Math.round((1f - alpha) * 8f);
        x += corner.left() ? -slide : slide;

        canvas.push(scale);
        return new int[]{x, y};
    }

    private static void panel(Canvas canvas, HudConfig config, int x, int y, int width, int height, float alpha) {
        int panelAlpha = Math.round(255 * (config.backgroundOpacity / 100f) * alpha);
        int radius = Math.min(CORNER, height / 3);
        roundedRect(canvas, x, y, width, height, radius, argb(COLOR_PANEL, panelAlpha));
        roundedOutline(canvas, x, y, width, height, radius, argb(COLOR_EDGE, Math.round(panelAlpha * 0.8f)));
    }

    /** Draws the album art (or a placeholder) and returns the x after it. */
    private static int cover(Canvas canvas, Track track, int x, int y, int size, float alpha) {
        artwork(canvas, track, x, y, size, alpha);
        return x + size + GAP;
    }

    /** Album art when there is one, a record for jukebox songs, a flat block otherwise. */
    private static void artwork(Canvas canvas, Track track, int x, int y, int size, float alpha) {
        Identifier art = CoverArt.get(track.artKey);
        if (art != null) {
            canvas.texture(art, x, y, size, size, CoverArt.TEXTURE_SIZE, alpha);
        } else if (track.artKey == null) {
            record(canvas, x, y, size, track.id.hashCode(), alpha);
        } else {
            roundedRect(canvas, x, y, size, size, Math.max(3, size / 8), argb(0x2A2A30, Math.round(255 * alpha)));
        }
    }

    /** A little vinyl: black disc, coloured label ring, centre hole. */
    private static void record(Canvas canvas, int x, int y, int size, int seed, float alpha) {
        int fullAlpha = Math.round(255 * alpha);
        int radius = size / 2;
        int[] palette = {0xD34F4F, 0x4F8FD3, 0x63C74D, 0xD3B24F, 0xA05FD3, 0x4FD3C0, 0xD37A4F};
        int label = palette[Math.floorMod(seed, palette.length)];
        for (int row = 0; row < size; row++) {
            double dy = row + 0.5 - radius;
            double half = Math.sqrt(Math.max(0, radius * radius - dy * dy));
            int span = (int) Math.round(half * 2);
            if (span <= 0) continue;
            int startX = x + radius - span / 2;
            canvas.rect(startX, y + row, span, 1, argb(0x121216, fullAlpha));

            double labelRadius = radius * 0.42;
            if (Math.abs(dy) < labelRadius) {
                double labelHalf = Math.sqrt(labelRadius * labelRadius - dy * dy);
                int labelSpan = (int) Math.round(labelHalf * 2);
                if (labelSpan > 0) {
                    canvas.rect(x + radius - labelSpan / 2, y + row, labelSpan, 1, argb(label, fullAlpha));
                }
            }
            if (Math.abs(dy) < 1.2) {
                canvas.rect(x + radius - 1, y + row, 2, 1, argb(0x101014, fullAlpha));
            }
        }
    }

    private static void progress(Canvas canvas, int x, int y, int width, int height, Track track, float alpha) {
        if (width <= 2) return;
        float fraction = track.durationMs <= 0
                ? 0f
                : Math.max(0f, Math.min(1f, track.currentProgressMs() / (float) track.durationMs));
        int filled = Math.round(width * fraction);
        int backing = argb(COLOR_TRACK, Math.round(200 * alpha));
        int fill = argb(COLOR_PROGRESS, Math.round(255 * alpha));
        if (height >= 3) {
            roundedRect(canvas, x, y, width, height, height / 2, backing);
            if (filled > 1) roundedRect(canvas, x, y, filled, height, height / 2, fill);
        } else {
            canvas.rect(x, y, width, height, backing);
            if (filled > 0) canvas.rect(x, y, filled, height, fill);
        }
    }

    private static void equaliser(Canvas canvas, int x, int centerY, Track track, float alpha, long now) {
        double seconds = now / 1_000_000_000.0;
        for (int i = 0; i < BAR_COUNT; i++) {
            double phase = seconds * 6.0 + i * 1.3;
            double wave = track.playing ? (Math.sin(phase) * 0.5 + Math.sin(phase * 0.6 + 1.1) * 0.5) : 0.0;
            int barHeight = track.playing
                    ? (int) Math.round(5 + Math.abs(wave) * 11)
                    : 4 + (i % 2) * 2;
            int barX = x + i * (BAR_WIDTH + BAR_GAP);
            int barY = centerY - barHeight / 2;
            canvas.rect(barX, barY, BAR_WIDTH, barHeight, argb(COLOR_BARS, Math.round(230 * alpha)));
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String timeLabel(Track track) {
        return Track.formatTime(track.currentProgressMs()) + " / " + Track.formatTime(track.durationMs);
    }

    private static void roundedRect(Canvas canvas, int x, int y, int width, int height, int radius, int color) {
        for (int row = 0; row < height; row++) {
            int inset = inset(row, height, radius);
            canvas.rect(x + inset, y + row, width - inset * 2, 1, color);
        }
    }

    private static void roundedOutline(Canvas canvas, int x, int y, int width, int height, int radius, int color) {
        int previous = -1;
        for (int row = 0; row < height; row++) {
            int inset = inset(row, height, radius);
            if (row == 0 || row == height - 1) {
                canvas.rect(x + inset, y + row, width - inset * 2, 1, color);
            } else {
                canvas.rect(x + inset, y + row, 1, 1, color);
                canvas.rect(x + width - inset - 1, y + row, 1, 1, color);
                if (previous >= 0 && previous != inset) {
                    canvas.rect(x + inset, y + row, previous - inset, 1, color);
                    canvas.rect(x + width - previous, y + row, previous - inset, 1, color);
                }
            }
            previous = inset;
        }
    }

    private static int inset(int row, int height, int radius) {
        if (radius <= 0) return 0;
        double distance = 0;
        if (row < radius) distance = (radius - 0.5) - row;
        else if (row >= height - radius) distance = row - (height - radius - 0.5);
        if (distance <= 0) return 0;
        double inner = radius * radius - distance * distance;
        if (inner <= 0) return radius;
        return (int) Math.round(radius - Math.sqrt(inner));
    }

    private static int argb(int rgb, int alpha) {
        return ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
