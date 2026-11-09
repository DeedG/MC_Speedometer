package fr.deedg.speedometer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Mesure la vitesse du joueur à partir de son déplacement réel entre deux ticks.
 * <p>
 * On utilise la position (et non la vélocité) parce que c'est la seule mesure fiable côté client :
 * elle inclut les véhicules (bateau, cheval, minecart…), l'élytre, les pistons, et même les
 * déplacements « par sauts » comme une highway de coussins (on clique, on est téléporté dessus).
 * <p>
 * Calcul : on prend le mouvement le plus récent et le plus ancien de la fenêtre (~1,2 s), et on divise
 * la distance parcourue entre les deux par le temps écoulé entre les deux. En déplacement continu,
 * c'est une moyenne sur 1,2 s (deux cycles de sprint-saut, donc stable). En déplacement par sauts,
 * c'est exactement « distance entre coussins / temps entre deux clics », sans oscillation.
 */
public final class SpeedTracker {
	/** Ticks par seconde du jeu. */
	private static final double TICKS_PER_SECOND = 20.0;
	/** Durée de la fenêtre de mesure : 24 ticks = 1,2 s = deux cycles de sprint-saut. */
	private static final int SPAN = 24;
	/** Historique conservé : permet de mesurer des sauts espacés jusqu'à 2 s (clics lents). */
	private static final int HISTORY = 40;
	/** Après N ticks sans aucun mouvement, on oublie l'historique. */
	private static final int RESET_DELAY = HISTORY;
	/** Délai minimum avant de rendre la main au niveau d'XP (3 = 0,15 s). */
	private static final int MIN_STOP_DELAY = 3;
	/** Au-delà de ce déplacement en un tick (= 400 blocs/s), on considère que c'est une téléportation. */
	private static final double TELEPORT_THRESHOLD = 20.0;
	/** En dessous de ce déplacement par tick, le joueur est considéré immobile ce tick-là. */
	private static final double STILL_EPSILON = 0.0025;
	/** Le chiffre affiché est rafraîchi toutes les N ticks (4 = 5 fois par seconde). */
	private static final int DISPLAY_INTERVAL = 4;

	/** Distances parcourues, indexées par l'âge du tick via {@link #at(int)}. */
	private static final double[] samples = new double[HISTORY];
	private static int head = 0;
	private static int sampleCount = 0;

	private static Vec3 lastPos = null;
	private static ClientLevel lastLevel = null;

	private static double speed = 0.0;
	private static boolean valid = false;
	private static double displayedSpeed = 0.0;
	private static int displayTimer = 0;

	/** Ticks écoulés depuis le dernier mouvement. */
	private static int stillTicks = RESET_DELAY;
	/** Écart (en ticks) entre les deux derniers mouvements : 1 en continu, ~5 sur des coussins. */
	private static int lastGap = 1;

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
		double dy = pos.y - lastPos.y;
		double dz = pos.z - lastPos.z;
		lastPos = pos;

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

		boolean moved = distance >= STILL_EPSILON;
		if (moved) {
			// Reprise après une vraie pause (bien plus longue que le rythme habituel) : on oublie
			// l'historique d'avant, sinon la pause tirerait la vitesse vers le bas pendant 1 s.
			if (valid && stillTicks >= 10 && stillTicks > 2 * lastGap + 2) {
				head = 0;
				sampleCount = 0;
				valid = false;
				displayedSpeed = 0.0;
				displayTimer = 0;
			}
			if (stillTicks < RESET_DELAY) {
				lastGap = stillTicks + 1;
			}
			stillTicks = 0;
		} else if (stillTicks < RESET_DELAY) {
			stillTicks++;
			if (stillTicks >= RESET_DELAY) {
				clearSamples();
				return;
			}
		}

		push(moved ? distance : 0.0);
		computeSpeed();

		// Le chiffre affiché n'est rafraîchi que toutes les DISPLAY_INTERVAL ticks pour rester lisible.
		// Au démarrage, on l'affiche tout de suite pour ne pas montrer 0.
		displayTimer++;
		if (displayTimer >= DISPLAY_INTERVAL || displayedSpeed == 0.0) {
			displayTimer = 0;
			displayedSpeed = valid ? speed : 0.0;
		}
	}

	/** Vitesse mesurée, en blocs par seconde (mise à jour à chaque tick). */
	public static double getSpeed() {
		return speed;
	}

	/** Vitesse à afficher : même valeur, mais rafraîchie moins souvent. */
	public static double getDisplayedSpeed() {
		return displayedSpeed;
	}

	/**
	 * Vrai tant que le joueur se déplace. Le délai d'arrêt s'adapte au rythme : en continu on rend
	 * la main à l'XP en 0,15 s, sur des coussins on attend un peu plus que l'écart entre deux clics
	 * pour que l'affichage ne clignote pas entre chaque saut.
	 */
	public static boolean isMoving() {
		int stopDelay = Math.max(MIN_STOP_DELAY, lastGap + 2);
		return valid && displayedSpeed > 0.0 && stillTicks < stopDelay;
	}

	private static void computeSpeed() {
		int count = Math.min(sampleCount, HISTORY);

		// Mouvement le plus récent.
		int newest = -1;
		for (int age = 0; age < count; age++) {
			if (at(age) > 0.0) {
				newest = age;
				break;
			}
		}
		if (newest < 0) {
			valid = false;
			return;
		}

		// Mouvement le plus ancien dans la fenêtre de SPAN ticks…
		int oldest = newest;
		for (int age = newest + 1; age < count && age - newest <= SPAN; age++) {
			if (at(age) > 0.0) {
				oldest = age;
			}
		}
		// … ou, s'il n'y en a qu'un (clics lents), le précédent dans tout l'historique.
		if (oldest == newest) {
			for (int age = newest + 1; age < count; age++) {
				if (at(age) > 0.0) {
					oldest = age;
					break;
				}
			}
		}
		if (oldest == newest) {
			// Un seul mouvement connu : pas encore de quoi mesurer une vitesse.
			valid = false;
			return;
		}

		// Distance parcourue après le mouvement le plus ancien, divisée par le temps écoulé.
		double sum = 0.0;
		for (int age = newest; age < oldest; age++) {
			sum += at(age);
		}
		speed = sum / (oldest - newest) * TICKS_PER_SECOND;
		valid = true;
	}

	/** Distance parcourue il y a {@code age} ticks (0 = ce tick-ci). */
	private static double at(int age) {
		return samples[Math.floorMod(head - 1 - age, HISTORY)];
	}

	private static void push(double distance) {
		samples[head] = distance;
		head = (head + 1) % HISTORY;
		sampleCount++;
	}

	private static void clearSamples() {
		head = 0;
		sampleCount = 0;
		speed = 0.0;
		valid = false;
		displayedSpeed = 0.0;
		displayTimer = 0;
		stillTicks = RESET_DELAY;
		lastGap = 1;
	}

	private static void reset() {
		clearSamples();
		lastPos = null;
		lastLevel = null;
	}
}
