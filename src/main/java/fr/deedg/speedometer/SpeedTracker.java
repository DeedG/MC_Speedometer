package fr.deedg.speedometer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Mesure la vitesse horizontale du joueur à partir de son déplacement réel entre deux ticks.
 * <p>
 * On utilise la position (et non la vélocité) parce que c'est la seule mesure fiable côté client :
 * elle inclut les véhicules (bateau, cheval, minecart…), l'élytre, les pistons, etc.
 */
public final class SpeedTracker {
	/** Ticks par seconde du jeu. */
	private static final double TICKS_PER_SECOND = 20.0;
	/** Nombre de ticks moyennés : lisse les oscillations (sprint-jump), soit 0,5 s. */
	private static final int WINDOW = 10;
	/** Le chiffre affiché est rafraîchi toutes les N ticks (4 = 5 fois par seconde). */
	private static final int DISPLAY_INTERVAL = 4;
	/** Après N ticks immobile, on rend la main au niveau d'XP (3 = 0,15 s). */
	private static final int STOP_DELAY = 3;
	/** Au-delà de ce déplacement en un tick (= 400 blocs/s), on considère que c'est une téléportation. */
	private static final double TELEPORT_THRESHOLD = 20.0;
	/** En dessous de cette vitesse (blocs/s), le joueur est considéré à l'arrêt. */
	private static final double MOVING_THRESHOLD = 0.05;

	private static final double[] samples = new double[WINDOW];
	private static int sampleIndex = 0;
	private static int sampleCount = 0;

	private static Vec3 lastPos = null;
	private static ClientLevel lastLevel = null;
	private static double speed = 0.0;
	private static double displayedSpeed = 0.0;
	private static int displayTimer = 0;
	/** Nombre de ticks consécutifs sans déplacement : sert à détecter l'arrêt sans attendre la moyenne. */
	private static int stillTicks = STOP_DELAY;

	private SpeedTracker() {
	}

	public static void tick(Minecraft client) {
		Player player = client.player;
		if (player == null || client.level == null) {
			reset();
			return;
		}

		Vec3 pos = player.position();
		if (lastPos == null || lastLevel != client.level) {
			reset();
			lastPos = pos;
			lastLevel = client.level;
			return;
		}

		double dx = pos.x - lastPos.x;
		double dz = pos.z - lastPos.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		lastPos = pos;

		if (distance > TELEPORT_THRESHOLD) {
			// Téléportation, perle de l'Ender, respawn… : on ignore ce tick.
			clearSamples();
			return;
		}

		if (distance * TICKS_PER_SECOND < MOVING_THRESHOLD) {
			stillTicks = Math.min(stillTicks + 1, STOP_DELAY);
			if (stillTicks >= STOP_DELAY) {
				// À l'arrêt : on repart de zéro pour que la reprise n'affiche pas l'ancienne vitesse.
				clearSamples();
				return;
			}
		} else {
			stillTicks = 0;
		}

		samples[sampleIndex] = distance;
		sampleIndex = (sampleIndex + 1) % WINDOW;
		if (sampleCount < WINDOW) {
			sampleCount++;
		}

		double sum = 0.0;
		for (int i = 0; i < sampleCount; i++) {
			sum += samples[i];
		}
		speed = sum / sampleCount * TICKS_PER_SECOND;

		// Le chiffre affiché n'est rafraîchi que toutes les DISPLAY_INTERVAL ticks pour rester lisible.
		// Au démarrage, on l'affiche tout de suite pour ne pas montrer 0.
		displayTimer++;
		if (displayTimer >= DISPLAY_INTERVAL || displayedSpeed < MOVING_THRESHOLD) {
			displayTimer = 0;
			displayedSpeed = speed;
		}
	}

	/** Vitesse horizontale lissée, en blocs par seconde (mise à jour à chaque tick). */
	public static double getSpeed() {
		return speed;
	}

	/** Vitesse à afficher : même valeur, mais rafraîchie moins souvent. */
	public static double getDisplayedSpeed() {
		return displayedSpeed;
	}

	public static boolean isMoving() {
		return stillTicks < STOP_DELAY && speed >= MOVING_THRESHOLD;
	}

	private static void clearSamples() {
		sampleIndex = 0;
		sampleCount = 0;
		speed = 0.0;
		displayedSpeed = 0.0;
		displayTimer = 0;
		stillTicks = STOP_DELAY;
	}

	private static void reset() {
		clearSamples();
		lastPos = null;
		lastLevel = null;
	}
}
