package fr.deedg.speedometer;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class SpeedometerClient implements ClientModInitializer {
	public static final String MOD_ID = "speedometer";

	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int LIGHT_SPEED_OUTLINE = 0xFF10206A;
	private static final int LIGHT_SPEED_FLASH = 0xFFFFFFFF;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(SpeedTracker::tick);

		// Masque le niveau d'XP vanilla tant que le joueur bouge ; il réapparaît dès l'arrêt.
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, original -> (graphics, deltaTracker) -> {
			if (!shouldShowSpeed()) {
				original.extractRenderState(graphics, deltaTracker);
			}
		});

		// Dessine la vitesse au même endroit. Attaché à la barre contextuelle plutôt qu'au niveau d'XP,
		// pour que l'affichage marche aussi au niveau 0 et en créatif (où le niveau n'est jamais dessiné).
		HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR,
				Identifier.fromNamespaceAndPath(MOD_ID, "speed"), SpeedometerClient::extractSpeed);
	}

	private static boolean shouldShowSpeed() {
		Player player = Minecraft.getInstance().player;
		return player != null && !player.isSpectator() && SpeedTracker.isMoving();
	}

	private static void extractSpeed(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!shouldShowSpeed()) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		double speed = SpeedTracker.getDisplayedSpeed();
		String text = String.format(Locale.ROOT, speed < 10 ? "%.2f" : "%.1f", speed);

		int color;
		int outline;
		if (speed >= SpeedColors.LIGHT_SPEED) {
			// Mode vitesse lumière : clignote blanc / bleu électrique ~6 fois par seconde.
			boolean flash = (System.currentTimeMillis() / 80) % 2 == 0;
			color = flash ? LIGHT_SPEED_FLASH : SpeedColors.forSpeed(speed);
			outline = LIGHT_SPEED_OUTLINE;
		} else {
			color = SpeedColors.forSpeed(speed);
			outline = OUTLINE_COLOR;
		}

		// Même position et même contour noir que le niveau d'XP vanilla.
		int x = (graphics.guiWidth() - font.width(text)) / 2;
		int y = graphics.guiHeight() - 24 - 9 - 2;
		graphics.text(font, text, x + 1, y, outline, false);
		graphics.text(font, text, x - 1, y, outline, false);
		graphics.text(font, text, x, y + 1, outline, false);
		graphics.text(font, text, x, y - 1, outline, false);
		graphics.text(font, text, x, y, color, false);
	}
}
