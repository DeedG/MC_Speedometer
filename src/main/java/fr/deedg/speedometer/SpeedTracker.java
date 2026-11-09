package fr.deedg.speedometer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Mesure la vitesse du joueur à partir de son déplacement réel entre deux ticks.
 * <p>
 * On utilise la position (et non la vélocité) parce que c'est la seule mesure fiable côté client :
 * elle inclut les véhicules (bateau, cheval, minecart…), l'élytre, les pistons, etc.
 * <p>
 * La valeur affichée est la distance parcourue pendant les dernières 1,2 s (fenêtre glissante
 * de 24 ticks). Une valeur instantanée (un seul tick) serait beaucoup plus instable : en
 * sprint-saut par exemple, la vitesse réelle varie à chaque tick entre l'air et le sol.
 */
public final class SpeedTracker {
	/** Ticks par seconde du jeu. */
	private static final double TICKS_PER_SECOND = 20.0;
	/**
	 * Fenêtre de moyenne : 24 ticks = 1,2 s, soit exactement deux cycles de sprint-saut (~12 ticks).
	 * Une fenêtre qui n'est pas un multiple du cycle fait osciller la valeur au rythme des sauts.
	 * Lisse aussi les à-coups (coussins, fusées d'élytre…).
	 */
	private static final int WINDOW = 24;
	/** Le chiffre affiché est rafraîchi toutes les N ticks (4 = 5 fois par seconde). */
	private static final int DISPLAY_INTERVAL = 4;
	/** Après N ticks immobile, on rend la main au niveau d'XP (3 = 0,15 s). */
	private static final int STOP_DELAY = 3;
	/**
	 * Après N ticks immobile, on vide l'historique : la reprise repart de zéro.
	 * Un arrêt plus court (s'asseoir une fraction de seconde sur un coussin…) ne casse pas la moyenne.
	 */
	private static final int RESET_DELAY = 20;
	/** Au-delà de ce déplacement en un tick (= 400 blocs/s), on considère que c'est une téléportation. */
	private static final double TELEPORT_THRESHOLD = 20.0;
	/** En dessous de ce déplacement par tick, le joueur est considéré immobile ce tick-là. */
	private static final double STILL_EPSILON = 0.0025;

	private static final double[] samples = new double[WINDOW];
	private static int sampleIndex = 0;
	private static int sampleCount = 0;
	private static double sampleSum = 0.0;

	private static Vec3 lastPos = null;
	private static ClientLevel lastLevel = null;
	private static Entity lastVehicle = null;

	private static double speed = 0.0;
	private static double displayedSpeed = 0.0;
	private static int displayTimer = 0;
	private static int stillTicks = RESET_DELAY;

	private SpeedTracker() {
	}

	public static void tick(Minecraft client) {
		Player player = client.player;
		if (player == null || client.level == null) {
			reset();
			return;
		}

		Vec3 pos = player.position();
		Entity vehicle = player.getVehicle();
		if (lastPos == null || lastLevel != client.level) {
			reset();
			lastPos = pos;
			lastLevel = client.level;
			lastVehicle = vehicle;
			return;
		}

		double dx = pos.x - lastPos.x;
		double dy = pos.y - lastPos.y;
		double dz = pos.z - lastPos.z;
		lastPos = pos;

		boolean mountChanged = vehicle != lastVehicle;
		lastVehicle = vehicle;

		// Vitesse 3D quand le déplacement vertical fait partie du trajet (élytre, vol, nage) ;
		// sinon horizontale, pour que les sauts ne gonflent pas la vitesse de marche.
		boolean count3d = player.isFallFlying() || player.getAbilities().flying || player.isSwimming();
		double distance = count3d
				? Math.sqrt(dx * dx + dy * dy + dz * dz)
				: Math.sqrt(dx * dx + dz * dz);

		if (distance > TELEPORT_THRESHOLD) {
			// Téléportation, perle de l'Ender, respawn… : on repart de zéro.
			clearSamples();
			return;
		}

		if (mountChanged) {
			// Monter / descendre d'un coussin, d'un bateau, d'un cheval… « téléporte » le joueur
			// d'un ou deux blocs : ce saut n'est pas une vitesse de déplacement, on ignore ce tick.
			return;
		}

		if (distance < STILL_EPSILON) {
			if (stillTicks < RESET_DELAY) {
				stillTicks++;
			}
			if (stillTicks >= RESET_DELAY) {
				clearSamples();
				return;
			}
		} else {
			stillTicks = 0;
		}

		addSample(distance);
		speed = sampleSum / sampleCount * TICKS_PER_SECOND;

		// Le chiffre affiché n'est rafraîchi que toutes les DISPLAY_INTERVAL ticks pour rester lisible.
		// Au démarrage, on l'affiche tout de suite pour ne pas montrer 0.
		displayTimer++;
		if (displayTimer >= DISPLAY_INTERVAL || displayedSpeed == 0.0) {
			displayTimer = 0;
			displayedSpeed = speed;
		}
	}

	/** Vitesse lissée sur les dernières 1,2 s, en blocs par seconde (mise à jour à chaque tick). */
	public static double getSpeed() {
		return speed;
	}

	/** Vitesse à afficher : même valeur, mais rafraîchie moins souvent. */
	public static double getDisplayedSpeed() {
		return displayedSpeed;
	}

	public static boolean isMoving() {
		return stillTicks < STOP_DELAY && displayedSpeed > 0.0;
	}

	private static void addSample(double distance) {
		if (sampleCount == WINDOW) {
			sampleSum -= samples[sampleIndex];
		} else {
			sampleCount++;
		}
		samples[sampleIndex] = distance;
		sampleSum += distance;
		sampleIndex = (sampleIndex + 1) % WINDOW;

		// Recalcul exact de temps en temps pour éviter la dérive des arrondis flottants.
		if (sampleIndex == 0) {
			double sum = 0.0;
			for (int i = 0; i < sampleCount; i++) {
				sum += samples[i];
			}
			sampleSum = sum;
		}
	}

	private static void clearSamples() {
		sampleIndex = 0;
		sampleCount = 0;
		sampleSum = 0.0;
		speed = 0.0;
		displayedSpeed = 0.0;
		displayTimer = 0;
		stillTicks = RESET_DELAY;
	}

	private static void reset() {
		clearSamples();
		lastPos = null;
		lastLevel = null;
		lastVehicle = null;
	}
}
