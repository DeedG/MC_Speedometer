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
	/** Nombre de ticks moyennés : lisse les oscillations (sprint-jump) sans trop de latence (0,25 s). */
	private static final int WINDOW = 5;
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
	}

	/** Vitesse horizontale lissée, en blocs par seconde. */
	public static double getSpeed() {
		return speed;
	}

	public static boolean isMoving() {
		return speed >= MOVING_THRESHOLD;
	}

	private static void clearSamples() {
		sampleIndex = 0;
		sampleCount = 0;
		speed = 0.0;
	}

	private static void reset() {
		clearSamples();
		lastPos = null;
		lastLevel = null;
	}
}
