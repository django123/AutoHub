# ADR 0003 — Machine à états de `Rental` : enum à transitions exhaustives

- **Statut** : accepté
- **Date** : 2026-10-12
- **Décideur** : Kutemodojika

## Contexte

L'agrégat `Rental` suit le cycle de vie de la roadmap §3.3 :

```
DRAFT → RESERVED → CONFIRMED → PICKED_UP → RETURNED → CLOSED
          │  └→ CANCELLED      └→ CANCELLED
          └→ EXPIRED
```

`CANCELLED`, `EXPIRED` et `CLOSED` sont terminaux. Certains états portent des
données qui n'ont de sens que pour eux : `reservedUntil` en `RESERVED`, l'état
des lieux de départ à partir de `PICKED_UP`, celui de retour à partir de
`RETURNED`.

Trois implémentations sont possibles en Java 21. Elles sont évaluées sur cinq
critères : lisibilité, testabilité, persistance, coût d'ajout d'un état, et
capacité à porter des données propres à un état.

| | (a) enum + méthodes abstraites | (b) sealed interface + `switch` | (c) `Map<Status, Set<Status>>` |
|---|---|---|---|
| Lisibilité | moyenne : la règle est éclatée sur huit corps de méthode | bonne par état, mauvaise vue d'ensemble | **excellente** : toute la table en un coup d'œil |
| Testabilité | bonne | bonne | **excellente** : test paramétré sur la table |
| Persistance | **triviale** (`name()`) | pénible : une classe par état, le mapper doit choisir laquelle instancier | **triviale** |
| Ajout d'un état | le compilateur force chaque méthode abstraite | le compilateur force chaque `switch` | **rien ne force** : on peut oublier la ligne de la table |
| Données propres à un état | non | **oui**, c'est son point fort | non |

## Décision

**Un `enum RentalStatus` dont la table de transitions est écrite dans un
`switch` exhaustif sur `this`** (méthode `allowedTargets()`).

```java
public Set<RentalStatus> allowedTargets() {
    return switch (this) {
        case DRAFT -> EnumSet.of(RESERVED);
        case RESERVED -> EnumSet.of(CONFIRMED, CANCELLED, EXPIRED);
        // ...
        case CLOSED, CANCELLED, EXPIRED -> EnumSet.noneOf(RentalStatus.class);
    };
}
```

C'est un hybride de (a) et (c) :

- on garde la **lisibilité d'une table** (c) : les transitions tiennent en huit
  lignes contiguës ;
- on gagne la **vérification du compilateur** : un `switch` expression sans
  `default` doit couvrir toutes les constantes, donc ajouter un état sans
  décider de ses transitions ne compile pas, ce que la `Map` de (c) ne
  garantit pas ;
- la **persistance reste triviale** : le nom de la constante dans une colonne
  `varchar`, protégée par un `CHECK` en base.

Les données propres à un état restent des champs de `Rental`. Leur cohérence
avec l'état est vérifiée à chaque transition, et à la relecture par
`Rental.restore()`, qui rejette par exemple un `PICKED_UP` sans état des lieux
de départ.

L'enum porte aussi `blocksVehicle()` (`RESERVED`, `CONFIRMED`, `PICKED_UP`).
Cette liste est dupliquée dans la clause `WHERE` de la contrainte d'exclusion
`rental_no_overlap`. Un test d'intégration lit la définition réelle de la
contrainte dans `pg_constraint` et la compare à l'enum.

## Alternatives écartées

### (a) Enum avec une méthode abstraite par transition
Chaque constante redéfinit `reserve()`, `confirm()`, `cancel()`... et lève une
exception pour les transitions interdites. On obtient 8 états × 6 transitions,
soit 48 corps de méthode, dont l'immense majorité ne fait que lever la même
exception. La règle « depuis quel état peut-on annuler ? » se lit en parcourant
huit blocs. Le comportement (enregistrer un événement, modifier un champ) ne
peut de toute façon pas vivre dans l'enum, qui n'a pas accès à l'agrégat.

### (b) Sealed interface `RentalState` + records par état
```java
sealed interface RentalState permits Draft, Reserved, Confirmed, ... {}
record Reserved(Instant reservedUntil) implements RentalState {}
record PickedUp(VehicleHandover pickup) implements RentalState {}
```
C'est la seule option qui rend `reservedUntil` **structurellement absent**
hors de `RESERVED`, au lieu de le vérifier. Sur le papier, c'est la plus
élégante. Elle est rejetée pour trois raisons :

1. **Le mapper devient un aiguillage** : à la relecture, il faut choisir quelle
   classe instancier selon la colonne `status`, puis lui passer les bonnes
   colonnes. Tout le gain de typage est perdu à la frontière de la persistance,
   là où se produisent justement les incohérences.
2. **Les données s'accumulent au fil des états.** `RETURNED` a besoin du
   constat de départ et du constat de retour, `CLOSED` aussi. Chaque record
   doit recopier les données du précédent, ou on revient à des champs dans
   l'agrégat.
3. **Le gain réel est faible ici** : une seule donnée vraiment propre à un état
   (`reservedUntil`). La vérification de cohérence dans `restore()` couvre le
   même risque pour une fraction du code.

À reconsidérer si une machine à états future porte beaucoup de données
exclusives à chaque état (la vente, au week-end 10, pourrait être ce cas).

### (c) Table statique `Map<RentalStatus, Set<RentalStatus>>`
Lisible et testable, mais un état ajouté à l'enum et oublié dans la table
compile parfaitement. Il obtient silencieusement « aucune transition », donc il
devient terminal par accident. Le `switch` exhaustif apporte la même lisibilité
sans ce défaut.

## Conséquences

### Positives
- Toutes les transitions se lisent en un seul endroit et se testent en un seul
  test paramétré (`RentalStatusTest`).
- Ajouter un état oblige à statuer sur ses transitions.
- Persistance en une colonne, sans logique dans le mapper.

### Négatives
- La présence de `reservedUntil` et des états des lieux est **vérifiée**, pas
  **garantie par le type**. Un bug dans `Rental` pourrait les désynchroniser
  avec l'état. `restore()` le détecterait au rechargement suivant, pas avant.
- `RentalStatus` est public : un appelant peut écrire
  `if (rental.status() == RESERVED)` et réintroduire une décision hors de
  l'agrégat. La parade est la revue de code, et les méthodes « Tell » de
  `Rental` (`expireIfOverdue`, `blocksVehicle`, `isLateReturn`).
