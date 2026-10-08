package dev.karton.spotifywidget.mixin;

import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.config.WidgetLayout;
import dev.karton.spotifywidget.hud.WidgetRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else
/*import net.minecraft.client.gui.Gui;*/

/**
 * Two small tweaks to the vanilla HUD text:
 * <ul>
 *   <li>the held item name drops by {@link WidgetRenderer#HOTBAR_DROP} pixels while the
 *       "above hotbar" design is on, so it keeps its distance from the widget line;</li>
 *   <li>the "now playing" disc name is suppressed while the widget shows the record itself.</li>
 * </ul>
 */
//? if >=26.2 {
@Mixin(Hud.class)
//?} else
/*@Mixin(Gui.class)*/
public class HudTextMixin {
    /** Vanilla draws the held item name at {@code guiHeight - 59}. */
    //? if >=26.1 {
    // require = 0: another HUD mod may rewrite this method; losing the nudge beats crashing the pack
    @ModifyConstant(method = "extractSelectedItemName", constant = @Constant(intValue = 59), require = 0)
    //?} else
    /*@ModifyConstant(method = "renderSelectedItemName", constant = @Constant(intValue = 59), require = 0)*/
    private int spotifywidget$lowerItemName(int original) {
        HudConfig config = HudConfig.get();
        if (!config.enabled || config.layout != WidgetLayout.HOTBAR) return original;
        return original - WidgetRenderer.HOTBAR_DROP;
    }

    @Inject(method = "setNowPlaying", at = @At("HEAD"), cancellable = true)
    private void spotifywidget$hideNowPlaying(Component name, CallbackInfo info) {
        HudConfig config = HudConfig.get();
        // The widget is about to show the same record, so drop the vanilla line
        if (config.enabled && config.jukeboxWidget) info.cancel();
    }
}
