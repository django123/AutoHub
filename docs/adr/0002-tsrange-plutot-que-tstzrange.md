# ADR 0002 — `tsrange` plutôt que `tstzrange` pour la période de location

- **Statut** : proposé
- **Date** : 2026-09-12
- **Décideur** : Kutemodojika

## Contexte

`DateRange` — et par extension le futur `RentalPeriod` — repose sur
`LocalDateTime`, une date-heure **nominale**, sans fuseau. Le choix est assumé
dans `BACKLOG.md` : AutoHub est mono-agence, « 18:00 » désigne 18:00 à l'agence.

La roadmap §3.3 prévoit en revanche une colonne `period tstzrange`, qui stocke
des **instants absolus**. Les deux types ne décrivent pas la même chose, et la
conversion de l'un vers l'autre passe nécessairement par un fuseau horaire.

L'enjeu n'est pas cosmétique : c'est sur cette colonne que repose la contrainte
`EXCLUDE USING gist`, c'est-à-dire l'invariant central du projet — l'impossibilité
physique d'une double réservation.

## Mesures

Confrontation de `DateRange.overlaps()` au moteur PostgreSQL 16 réel.

**3 000 paires d'intervalles tirées au hasard** (granularité horaire, sur 30 jours) :
zéro désaccord, aussi bien avec `tsrange` qu'avec `tstzrange`. La formule
`start < other.end && other.start < end` est bien celle de l'opérateur `&&` sur
un intervalle `'[)'`.

**Jeu de cas limites choisis** : trois divergences apparaissent.

| Cas | Java | `tsrange` | `tstzrange` (Europe/Paris) |
|---|---|---|---|
| Intervalles adjacents | pas de chevauchement | idem | idem |
| Chevauchement d'une nanoseconde | chevauchement | **non** | **non** |
| Intervalle d'une nanoseconde | chevauchement | **non** | **non** |
| 02:00 → 03:00 le 29 mars (heure locale inexistante) | chevauchement, durée 1 h | chevauchement | **non**, durée 0 |

Vérification sur la contrainte d'exclusion réelle de la roadmap §3.3 :

| Scénario | Type | Lignes acceptées | |
|---|---|---|---|
| Deux locations chevauchantes, dates ordinaires | `tstzrange` | 1 | la contrainte joue |
| Les mêmes, sur l'heure du passage à l'heure d'été | `tstzrange` | **2** | **double réservation acceptée** |
| Le même cas | `tsrange` | 1 | la contrainte joue |

**Explication.** Le 29 mars 2026 à 02:00, l'heure locale française n'existe pas :
les pendules passent de 02:00 à 03:00. `timestamp '02:00' AT TIME ZONE 'Europe/Paris'`
et `timestamp '03:00' AT TIME ZONE 'Europe/Paris'` désignent donc **le même instant**.
L'intervalle `tstzrange` correspondant est *vide*, et PostgreSQL considère qu'un
intervalle vide ne chevauche rien. La contrainte ne se déclenche pas.

La fenêtre est étroite — une heure par an — mais le mode de défaillance est
exactement celui que toute l'architecture vise à rendre impossible, et il est
silencieux.

## Décision

**1. La période de location est stockée en `tsrange`, sémantique `'[)'`.**

C'est le type qui correspond exactement à `LocalDateTime` : aucune conversion,
aucun fuseau, aucun changement d'heure. Le modèle Java et la contrainte
d'exclusion partagent alors rigoureusement la même définition du chevauchement,
ce qui était l'objectif annoncé du choix semi-ouvert (README §5).

`btree_gist` reste nécessaire et `EXCLUDE USING gist (vehicle_id WITH =, period WITH &&)`
fonctionne à l'identique : `tsrange` est un type de plage comme un autre.

**2. Le domaine refuse les intervalles de précision inférieure à la microseconde.**

`timestamp` a une précision de 1 µs, `LocalDateTime` de 1 ns. Un intervalle d'une
nanoseconde est valide côté Java mais se projette en intervalle **vide** côté base
— là encore, la contrainte ne se déclencherait pas. Le défaut concerne `tsrange`
comme `tstzrange`, et se corrige dans le domaine : soit en tronquant les bornes à
la microseconde à la construction, soit en exigeant une durée minimale d'une
microseconde. À trancher au week-end 4, avec `RentalPeriod`.

## Alternatives écartées

### Conserver `tstzrange` avec `LocalDateTime` dans le domaine
Rejeté — c'est la combinaison mesurée ci-dessus, et elle est démontrée fausse.

### `tstzrange` avec `Instant` ou `ZonedDateTime` dans le domaine
Correct, et ce serait le bon choix pour un opérateur multi-fuseaux. Rejeté ici
pour deux raisons : le multi-agences est hors périmètre (roadmap §1), et le
domaine y perdrait en lisibilité — « le client rend la voiture à 18:00 » devient
un calcul au lieu d'un fait. Le coût ne se justifie qu'une fois la deuxième
agence ouverte.

### Stocker en `tstzrange` en convertissant tout en UTC
Revient à décréter que les heures nominales sont des heures UTC. Fonctionne
(aucun changement d'heure en UTC) mais produit une base dont les valeurs ne
correspondent à rien de lisible pour un exploitant français : une location de
08:00 s'y affiche à 08:00 UTC, soit 10:00 locales en été. On paie la complexité
de `tstzrange` sans en tirer le bénéfice.

## Conséquences

### Positives
- Le code et la base ne peuvent plus se contredire sur un cas limite : il n'y a
  plus de conversion entre eux.
- La contrainte d'exclusion protège l'invariant **toute** l'année.
- Le mapper JPA est plus simple : `LocalDateTime` ↔ `timestamp`, sans fuseau.

### Négatives
- Le jour où AutoHub ouvrira une agence hors métropole, il faudra une migration
  de données, pas seulement un changement de type. C'est le coût assumé de la
  dette déjà inscrite au `BACKLOG.md`.
- On s'écarte de la roadmap §3.3, qui prévoyait `tstzrange`. La roadmap est un
  document de travail « à réviser à la fin de chaque phase » ; c'est le cas.

### Vérification
Au week-end 5, deux tests d'intégration à écrire en plus du test de concurrence
à 50 threads :

1. Deux locations sur le créneau 02:00 → 03:00 du dernier dimanche de mars : la
   seconde doit être rejetée par la contrainte.
2. Deux locations adjacentes (l'une finit quand l'autre commence) : les deux
   doivent être acceptées — c'est le scénario qui justifie l'intervalle
   semi-ouvert, et il doit rester vrai après ce changement de type.
