# ADR 0004 — Communication entre `rental` et `fleet` : événement après commit

- **Statut** : accepté
- **Date** : 2026-10-12
- **Décideur** : Kutemodojika

## Contexte

Au retour d'un véhicule, le kilométrage relevé (contexte `rental`) doit mettre
à jour le kilométrage de l'agrégat `Vehicle` (contexte `fleet`). C'est la
première fois qu'un contexte doit faire agir l'autre. La décision fixe la
forme de toutes les interactions à venir, à commencer par la greffe de `sales`
au WE8.

Quatre options ont été examinées :

| | Couplage de code | Si la mise à jour échoue | Survit au découpage en services |
|---|---|---|---|
| (a) `rental` appelle `VehicleRepository` | `rental` → `fleet` (repository) | le retour est annulé | non |
| (b) `rental` appelle un cas d'usage de `fleet` | `rental` → `fleet` (port entrant) | le retour est annulé | oui, en appel distant synchrone |
| (c) événement, listener dans la **même** transaction | `fleet` → événement de `rental` | le retour est annulé | non (transaction distribuée) |
| (d) événement, listener **après** le commit | `fleet` → événement de `rental` | le retour reste valide | oui, sans changement de modèle |

Transposée à `fleet`, la règle ArchUnit « `rental` ne dépend pas de `sales` »
est violée par (a) et (b). (c) et (d) n'introduisent qu'une dépendance de
`fleet` vers les **événements** de `rental`.

## Décision

**Option (d).** `Rental.returnVehicle()` enregistre `VehicleReturned`. Le cas
d'usage remet cet événement à l'`EventPublisher` pendant sa transaction, et
`VehicleReturnedListener` (dans `fleet`) le reçoit **après le commit**
(`@TransactionalEventListener(phase = AFTER_COMMIT)`). Le listener appelle
alors le cas d'usage `RecordReturnMileageUseCase` dans une transaction neuve
(`REQUIRES_NEW`).

Mesures d'accompagnement :

1. **`VehicleId` passe dans le noyau partagé** (`shared.domain`). Il vivait
   dans `fleet.domain.model`, ce qui faisait déjà dépendre `Rental` de `fleet`.
   Un identifiant sans comportement est la seule chose qu'on partage.
2. **Deux règles ArchUnit, écrites avant le code** :
   - `rental` ne dépend pas de `fleet` ;
   - `fleet` ne dépend de `rental` que par `rental.domain.event`.
   On a vérifié que la première mord, en y introduisant une dépendance
   volontaire.
3. **`rental` lit le parc par sa propre couche anti-corruption.** Le port
   `VehicleCatalog` est implémenté en SQL par `JdbcVehicleCatalog`, sans
   importer de classe `fleet`.
4. **Le consommateur est idempotent.** `Vehicle.recordReturnMileage()` ignore
   un relevé égal (événement rejoué) ou inférieur (événement en retard) au
   kilométrage courant.

## Le compromis : cohérence immédiate contre découplage

La question à se poser n'était pas « quelle option est la plus propre ? »,
mais **« le retour d'un véhicule doit-il échouer si le kilométrage du parc ne
peut pas être mis à jour ? »**.

La réponse métier est non. Le client a rendu la voiture, l'agent a constaté le
compteur, et ce fait est irréversible. Refuser d'enregistrer le retour parce
qu'une autre partie du système est indisponible serait absurde au guichet.
Les options (a), (b) et (c) lient pourtant le sort du retour à celui de la
mise à jour. Elles achètent une cohérence immédiate dont le métier n'a pas
besoin, au prix d'un couplage dont l'architecture ne veut pas.

## Ce qu'on accepte de perdre

- **La cohérence immédiate.** Pendant quelques millisecondes, et indéfiniment
  si le listener échoue, le kilométrage de `Vehicle` est en retard sur le
  dernier retour. Personne ne prend de décision métier sur ce chiffre dans
  cet intervalle.
- **La garantie de livraison.** Entre le commit et la livraison, l'événement
  n'existe qu'en mémoire : un arrêt de l'application à cet instant le perd.
  Le prochain retour, dont le relevé est plus élevé, corrige l'écart. En
  attendant, l'échec est journalisé en ERROR avec de quoi rejouer la mise à
  jour à la main. **La table outbox (phase 5) supprimera cette perte** en
  écrivant l'événement dans la même transaction que la location.
- **La simplicité de lecture.** Un développeur qui lit `ReturnVehicleService`
  ne voit pas la mise à jour du kilométrage : il doit savoir qu'un listener
  existe. La javadoc du service le signale.

## Alternatives écartées

### (a) Appel direct de `VehicleRepository` depuis `rental`
C'est le plus court à écrire, et le pire couplage. `rental` connaîtrait
l'agrégat `Vehicle`, sa persistance et ses invariants. Deux agrégats seraient
modifiés dans une même transaction, ce qui verrouille deux lignes et ouvre la
porte aux conflits de version croisés. Au WE8, `sales` voudrait faire de même,
et le parc deviendrait le carrefour de tous les contextes. Interdit par
ArchUnit.

### (b) Appel d'un cas d'usage de `fleet`
Plus propre que (a) : `rental` ne connaît qu'un port. Mais `rental` dépend
toujours de `fleet`, et un échec de `fleet` fait échouer le retour. En
microservices, cela devient un appel HTTP synchrone dans le chemin critique du
retour, avec ses délais d'attente et ses pannes en cascade.

### (c) Événement traité dans la même transaction
Le découplage du code est réel, mais le couplage transactionnel reste : une
exception dans `fleet` annule le retour dans `rental`. Ce sont les
inconvénients de (b), cachés derrière une indirection. Cette option ne
survivrait pas au découpage en services sans transaction distribuée.

## Conséquences

### Positives
- `rental` ignore jusqu'à l'existence de `Vehicle`. La greffe de `sales` suivra
  le même chemin.
- Le contrat entre contextes est explicite : les classes de
  `rental.domain.event`.
- Le passage à Kafka (janvier) ne change que l'adaptateur de publication et
  le listener, pas les cas d'usage.

### Négatives
- `fleet` dépend des classes d'événement de `rental`. Renommer un champ de
  `VehicleReturned` casse `fleet` à la compilation. C'est mieux qu'en
  production, mais c'est un couplage réel.
- `JdbcVehicleCatalog` duplique deux règles de `fleet` et de la base : la
  définition de « louable » et la liste des statuts bloquants. Elles sont
  couvertes par le test HTTP de bout en bout, mais rien ne force leur
  alignement à la compilation.
