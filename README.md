# Speedometer

Mod Fabric **100 % côté client** pour Minecraft **26.3** : affiche ta vitesse horizontale en blocs par seconde à la place du niveau d'XP, uniquement quand tu bouges. Dès que tu t'arrêtes, le niveau d'XP vanilla revient.

Aucun mixin, aucun paquet réseau : fonctionne sur n'importe quel serveur (vanilla, Paper, etc.) sans rien installer côté serveur.

## Installation

1. Fabric Loader ≥ 0.19.5 pour Minecraft 26.3
2. [Fabric API](https://modrinth.com/mod/fabric-api) pour 26.3
3. Dépose `speedometer-x.y.z.jar` dans `mods/`

Le jar est produit à chaque push par GitHub Actions (onglet **Actions** → dernier build → artefact `speedometer`).

## Couleurs

La couleur glisse progressivement d'un palier à l'autre (vitesses tirées du [wiki](https://minecraft.wiki/w/Transportation)) :

| Vitesse (b/s) | Repère en jeu | Couleur |
|---|---|---|
| 0 – 1,3 | accroupi | gris |
| 4,3 | marche | blanc |
| 5,6 | sprint | vert (XP vanilla) |
| 7,1 | sprint + saut | vert-jaune |
| 8 | minecart, bateau sur l'eau, chameau | jaune |
| 11 | cheval moyen, vol créatif | orange |
| 14,6 | cheval le plus rapide | orange foncé |
| 22 | vol créatif en sprint | rouge |
| 33,5 | élytre + fusées | rose |
| 40 | bateau sur glace | violet |
| ≥ 60 | glace bleue (72,7), piqué en élytre (67–78) | **vitesse lumière** : clignote blanc / bleu électrique |

## Fonctionnement

La vitesse est mesurée à partir du déplacement réel de la position du joueur entre deux ticks (moyenne glissante sur 5 ticks), donc elle marche aussi en véhicule (bateau, cheval, minecart…) et en élytre. Les téléportations sont ignorées. Rien n'est affiché en spectateur.

## Compiler

```
./gradlew build
```

Java 25 requis. Le jar se trouve dans `build/libs/`.
