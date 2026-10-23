package fr.deedg.speedometer;

/**
 * Palette de couleurs en fonction de la vitesse, calée sur les vitesses du jeu
 * (source : https://minecraft.wiki/w/Transportation). La couleur est interpolée
 * en douceur entre chaque palier.
 */
public final class SpeedColors {
	/** Au-delà de cette vitesse (blocs/s) : mode « vitesse lumière » (bateau sur glace bleue, piqué en élytre). */
	public static final double LIGHT_SPEED = 60.0;

	/** Vert du niveau d'XP vanilla. */
	public static final int VANILLA_XP_GREEN = 0xFF80FF20;

	// Paliers : { vitesse en blocs/s, couleur RGB }
	private static final double[] SPEEDS = {
			0.0,    // immobile / à peine
			1.3,    // accroupi
			4.317,  // marche
			5.612,  // sprint
			7.127,  // sprint + saut
			8.0,    // minecart, bateau sur l'eau, chameau au sprint
			11.0,   // cheval moyen / vol en créatif
			14.57,  // cheval le plus rapide possible
			22.0,   // vol créatif en sprint
			33.5,   // élytre + fusées en continu
			40.0,   // bateau sur glace / glace compactée
			LIGHT_SPEED
	};

	private static final int[] COLORS = {
			0x8A8A8A, // gris
			0xB5B5B5, // gris clair
			0xFFFFFF, // blanc
			0x80FF20, // vert XP vanilla
			0xC8FF3C, // vert-jaune
			0xFFFF55, // jaune
			0xFFAA00, // orange
			0xFF7A1A, // orange foncé
			0xFF3333, // rouge
			0xFF40C0, // rose
			0xB45CFF, // violet
			0x55DDFF  // bleu électrique
	};

	private SpeedColors() {
	}

	/** Couleur ARGB (opaque) correspondant à la vitesse donnée. */
	public static int forSpeed(double speed) {
		if (speed <= SPEEDS[0]) {
			return 0xFF000000 | COLORS[0];
		}
		for (int i = 1; i < SPEEDS.length; i++) {
			if (speed < SPEEDS[i]) {
				double t = (speed - SPEEDS[i - 1]) / (SPEEDS[i] - SPEEDS[i - 1]);
				return 0xFF000000 | lerp(COLORS[i - 1], COLORS[i], t);
			}
		}
		return 0xFF000000 | COLORS[COLORS.length - 1];
	}

	private static int lerp(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
