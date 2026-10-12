# Sprint WE6 + WE7 — Notes : agrégat Vehicle, cas d'usage, API REST, tarification, retour

Ce document répond aux blocs « explique-moi », « revue » et « bilan » de
`session-we6-we7-claude-code.md`. Comme au sprint précédent, le code a été
généré à ta demande ; ces notes sont ce qu'il faut relire et savoir défendre.
Le WE3 (agrégat `Vehicle` complet), sauté jusqu'ici, a été réalisé dans le
même mouvement.

---

## Bloc 6.0 — Reprise

`mvn verify` était vert au démarrage, ArchUnit compris. Il n'y avait rien à
réparer. Le seul risque de blocage était l'absence de l'agrégat `Vehicle`,
nécessaire à la recherche de disponibilités et au retour du véhicule.

---

## Bloc 6.1 — La couche application

**1. Une classe par cas d'usage, ou un `RentalApplicationService` ?**

Une classe par cas d'usage : `ReserveRentalService`, `ConfirmRentalService`,
etc.

- Le service unique a pour lui la découvrabilité, les dépendances partagées et
  moins de fichiers.
- Mais au bout de 15 cas d'usage, c'est une classe de 800 lignes qui dépend de
  tout, et chaque test doit construire toutes ses dépendances. Un contrôleur
  qui ne fait que confirmer recevrait aussi le catalogue et la tarification.

Avec une classe par cas d'usage, chacune n'a que ses dépendances : quatre
pour `ConfirmRentalService`, six pour `ReserveRentalService`. Les gestes
communs (charger ou lever 404, enregistrer puis publier) sont factorisés dans
une classe utilitaire de paquet (`Rentals`), pas dans une classe mère.

**2. Où placer la transaction ?** Sur le cas d'usage : c'est lui qui sait
quelles étapes doivent réussir ensemble. Ni sur le contrôleur (la transaction
durerait pendant la sérialisation JSON, et un job ne serait pas
transactionnel), ni sur le repository (chaque `save` serait isolé, et le
verrouillage optimiste ne protégerait plus rien). Et surtout pas dans le
domaine : un agrégat est une frontière de cohérence, il ignore s'il est
sauvegardé.

La couche application n'ayant pas Spring, `@Transactional` y est impossible.
La frontière passe par un port, `UnitOfWork` (**ADR 0005**).

**3. Que renvoie un cas d'usage ?** Une commande renvoie un identifiant
(`UUID reserve(...)`), une requête renvoie une vue (`RentalView`).

- Renvoyer l'agrégat exposerait le domaine au contrôleur : il pourrait le
  modifier, ou le sérialiser tel quel.
- Renvoyer une vue depuis une commande mélangerait écriture et lecture.

Les ports n'utilisent que des types standard (UUID, `LocalDateTime`,
`MoneyView`). Une règle ArchUnit interdit aux contrôleurs d'importer le
domaine.

**4. Qui récolte les événements, et quand ?** Le cas d'usage, juste après le
`save` et dans la transaction (`Rentals.saveAndPublish`). L'`EventPublisher`
les remet au bus Spring, qui ne les **délivre** aux consommateurs qu'après le
commit (`@TransactionalEventListener(AFTER_COMMIT)`).

Publier réellement avant le commit est un bug, pas un détail de timing. Si la
transaction est annulée ensuite (conflit de période au flush, par exemple),
un consommateur aura déjà agi sur un fait qui n'a jamais eu lieu. Il aura par
exemple mis à jour un kilométrage pour un retour annulé.

---

## Bloc 6.2 — Le TTL de réservation

| | (a) Job `@Scheduled` | (b) Expiration paresseuse à la lecture | (c) `pg_cron` |
|---|---|---|---|
| Concurrence | conflit de version possible avec une confirmation simultanée : traité (ignoré) | deux lecteurs expirent la même ligne : l'un perd sur la version | sûr, une seule instance SQL |
| Contrainte d'exclusion | créneau gelé jusqu'au passage du job | ne libère que ce qu'on relit | créneau gelé jusqu'au passage |
| Redémarrage | rattrape au premier passage | sans objet | indépendant de l'application |
| Testabilité | excellente (`Clock` injecté, appel direct) | bonne | mauvaise (temps réel, extension) |
| Logique métier | dans `Rental.expireIfOverdue` | dans `Rental` | **dupliquée en SQL** |

**Recommandation retenue : (a) + une expiration ciblée au moment de la
réservation.** Le piège annoncé par la session est réel : tant que la ligne
est `RESERVED`, la contrainte bloque le créneau. Plutôt que de raccourcir
l'intervalle du job, ce qui réduit la fenêtre sans la supprimer,
`ReserveRentalService` fait expirer, dans sa propre transaction, les
réservations échues qui chevauchent la période demandée. Le créneau se libère
donc exactement quand quelqu'un le demande. Le job (toutes les minutes) ne
sert plus qu'à faire passer les statuts à `EXPIRED` et à publier
`RentalExpired`. Un job en retard de 30 secondes ne gêne plus personne.

La recherche de disponibilités ignore aussi les réservations échues, pour
rester cohérente. Le test `reservation_echue_liberee` (HTTP) le prouve.

**À 10 000 réservations par jour**, ce choix tient. Il y a au plus environ
200 réservations non confirmées en même temps, et l'index partiel
`rental_overdue_reservation_idx` sert la requête du job. Il casse plutôt avec
plusieurs instances de l'application. Chaque instance exécute le job : le
résultat reste correct (idempotent, conflits ignorés), mais le travail est fait
en double. Au-delà, il faudrait un verrou de planification (ShedLock) ou un
`SELECT ... FOR UPDATE SKIP LOCKED` par lot.

---

## Bloc 6.3 — Les ports entrants

| Port | Signature | Invariant garanti |
|---|---|---|
| `ReserveRentalUseCase` | `UUID reserve(ReserveRentalCommand)` | véhicule louable, créneau libre, prix figé, expiration à 30 min |
| `ConfirmRentalUseCase` | `void confirm(UUID)` | seulement avant l'échéance |
| `CancelRentalUseCase` | `void cancel(UUID, String)` | seulement avant le départ ; libère le créneau |
| `PickUpVehicleUseCase` | `void pickUp(UUID, HandoverCommand)` | seulement confirmée, pendant la période |
| `ReturnVehicleUseCase` | `void returnVehicle(UUID, HandoverCommand)` | compteur croissant ; pénalité si retard |
| `CloseRentalUseCase` | `void close(UUID)` | seulement une fois rendue |
| `GetRentalQuery` | `RentalView get(UUID)` | lecture seule |
| `FindAvailableVehiclesUseCase` | `List<AvailableVehicleView> findAvailable(start, end)` | louables, sans réservation bloquante non échue |
| `ExpireOverdueReservationsUseCase` | `int expireOverdueReservations()` | idempotent, une transaction par réservation |

Les tests des cas d'usage (`RentalUseCasesTest`, `FleetUseCasesTest`) tournent
**sans Spring ni Mockito**. Le fake `InMemoryRentalRepository` fait 70 lignes, javadoc comprise,
parce qu'il imite la contrainte d'exclusion. Sans cela, on ne pourrait pas
montrer qu'il faut expirer *avant* d'enregistrer.

---

## Bloc 6.4 — Double POST à 200 ms d'intervalle

1. **Ce que fait la contrainte.** Les deux requêtes portent sur le même
   véhicule et la même période. La première insère. La seconde, soit attend
   le verrou consultatif de l'adaptateur puis échoue en `23P01`, soit arrive
   après le commit et échoue directement. Il n'y a jamais de double
   réservation.
2. **Pourquoi le 409 est fonctionnellement mauvais.** Le client a reçu, pour
   la *même intention*, une réponse de succès qu'il n'a peut-être jamais vue
   (timeout réseau), puis un `409 VEHICLE_NOT_AVAILABLE`. Il conclut que la
   réservation a échoué alors qu'elle existe. Il réessaie sur un autre
   véhicule et se retrouve avec deux réservations, ou il abandonne alors
   qu'il est servi.
3. **`Idempotency-Key`.** Le client génère une clé (UUID) par intention et
   l'envoie en en-tête. Le serveur stocke `(clé, empreinte du corps, statut
   et corps de la réponse, date)` dans une table à clé primaire sur la clé,
   **dans la même transaction** que la réservation. Une seconde requête avec
   la même clé rejoue la réponse enregistrée. Avec la même clé mais un autre
   corps, elle reçoit un 422. Les entrées sont purgées après 24 h.
4. **À ton échelle :** au BACKLOG. C'est indispensable dès qu'un client mobile
   ou un paiement est en jeu, superflu tant que l'API n'a pas de vrai client.

---

## Bloc 6.5 — Verrouillage optimiste de bout en bout

1. **Exception levée, et quand.** Hibernate émet
   `UPDATE rental ... WHERE id = ? AND version = ?`. Si aucune ligne n'est
   touchée, il lève `StaleObjectStateException` **au flush**, pas au `save`.
   C'est pourquoi l'adaptateur fait `saveAndFlush`.
2. **Enveloppe Spring :** `ObjectOptimisticLockingFailureException`, sous-classe
   d'`OptimisticLockingFailureException`.
3. **Traduction :** dans `RentalRepositoryAdapter`, en
   `ConcurrentRentalModificationException` (domaine, catégorie
   `ConflictException`). Pour `fleet`, c'est `VehicleRepositoryAdapter` qui
   lève `ConcurrentVehicleModificationException`.
4. **Code HTTP : 409.** Ce n'est pas une panne (500) : la requête est valide,
   mais elle a été devancée. Le client doit recharger la ressource et décider
   à nouveau.

Le test d'intégration est `RentalRepositoryAdapterTest.mise_a_jour_perdue_evitee`,
et la traduction HTTP est couverte par `GlobalExceptionHandlerTest`.

---

## Clôture WE6 — Bilan critique

1. **Terminé :**
   - parcours réserver → confirmer jouable en HTTP (`RentalHttpJourneyTest`,
     et joué à la main sur l'application lancée) ;
   - cas d'usage testés sans Spring ;
   - aucun agrégat dans le JSON (test dédié) ;
   - expiration qui libère le créneau (test HTTP) ;
   - 409 sur conflit de version.
2. **À moitié fait :**
   - aucune authentification (voir BACKLOG) : `customerId` est déclaratif ;
   - les événements peuvent se perdre entre le commit et la livraison (outbox
     en phase 5).
3. **ArchUnit :** 12 règles, toutes vertes. Trois règles ont été ajoutées :
   - les contrôleurs ne dépendent ni du domaine ni des services ;
   - `rental` ↛ `fleet` ;
   - `fleet` ↛ `rental`, sauf ses événements.
4. **En entretien :** pourquoi `UnitOfWork` plutôt que `@Transactional`, et
   pourquoi le listener a besoin de `REQUIRES_NEW` en `AFTER_COMMIT`.

**Découvert en exécutant :** sans `spring-boot-starter-parent`, le flag
`-parameters` n'était pas actif. Chaque route avec `@PathVariable UUID id`
répondait 500. Il est ajouté au `pom.xml` parent.

---

## Bloc 7.1 — Composer des politiques

1. **Interface :** `Optional<PriceLine> apply(PricingContext, Money subtotal)`.
   La politique reçoit le sous-total courant, ce qui est la forme la plus
   simple qui compose. Recevoir la liste des ajustements déjà appliqués serait
   plus expressif, mais personne n'en a besoin.
2. **Non commutatif.** Sur 30 jours à 100 € (3 000 €) :
   - remise de 20 % puis majoration de 15 € : 2 400 + 15 = **2 415 €** ;
   - majoration puis remise : 3 015 × 0,8 = **2 412 €**.

   Le test `non_commutativite` le prouve.
3. **Rendre l'ordre explicite.** L'ordre est une `List` construite dans
   `PricingService.standard()`, et aucune politique n'est un bean Spring.
   Deux approches ont été écartées :
   - `@Order` sur des beans : l'ordre est éparpillé, et le domaine dépendrait
     de Spring ;
   - une méthode `priority()` : même éparpillement, et deux priorités égales
     ramènent l'indéterminisme.
4. **Arrondi : à chaque ligne** (option 1 du BACKLOG). Sur 7 jours à 10,05 € :
   - la remise de 10 % vaut 7,035 €, affichée 7,04 €, d'où un total de **63,31 €** ;
   - un arrondi unique à la fin donnerait **63,32 €**.

   On garde 63,31 € : la facture affiche des lignes que le client peut
   vérifier, et leur somme est exactement le total.

---

## Bloc 7.2 — Les tests de tarification

Dans `PricingServiceTest`, avec un astérisque sur les cas souvent oubliés :

- `TarifDeBase` : `jours_pleins`, `* jour_entame` (25 h = 2 jours)
- `RemiseLongueDuree` : `six_jours`, `sept_jours_pile`, `* juste_sous_le_seuil`
  (6 j 23 h : facturé 7 jours, **sans** remise, car le seuil porte sur la
  durée réelle), `trente_jours`
- `CompositionEtOrdre` : `* non_commutativite`, `* ordre_de_la_chaine_standard`,
  `determinisme`, `total_egal_somme_des_lignes`
- `Arrondi` : `* arrondi_de_la_remise`, `* arrondi_par_ligne_contre_arrondi_final`
- `GardeFous` : `* total_non_positif`, `chaine_vide`

---

## Bloc 7.3 — `pickUp` / `returnVehicle`

Les règles demandées sont dans `Rental` :

- `pickUp()` n'est possible que depuis `CONFIRMED` ;
- au retour, le kilométrage ne recule pas ;
- un retour après `period.end()` déclenche une pénalité : 150 % du tarif
  journalier **figé** par jour entamé, avec l'événement
  `LateReturnPenaltyApplied`.

**Décision métier consignée** dans la javadoc de `returnVehicle` : un retour
anticipé ne donne lieu à **aucun remboursement**. Le véhicule a été bloqué sur
toute la période ; il redevient en revanche louable dès son retour.

---

## Bloc 7.4 — Mise à jour du kilométrage entre contextes

Voir **ADR 0004**, qui retient l'option (d) : événement, réaction après le
commit. Les règles ArchUnit ont été écrites avant le code. On a vérifié que
`rental` ↛ `fleet` mord, en introduisant volontairement un champ
`VehicleRepository` dans `rental` : le test a échoué, puis la sonde a été
supprimée.

---

## Bloc 7.5 — Revue critique (sans correction)

| # | Constat | Conséquence en production | Correction proposée |
|---|---|---|---|
| 1 | Prix déterministe : l'ordre est dans `PricingService.standard()`, aucune politique n'est un bean. Prouvé par `ordre_de_la_chaine_standard`. | — | — |
| 2 | `LongDurationDiscountPolicy` ne peut pas rendre le total négatif (20 % au plus). Une politique future le pourrait. `PricingService.quote` le refuse (`PRICE_NOT_POSITIVE`), voir `total_non_positif`. | Une erreur de réglage donne un 422, pas une location gratuite. | — |
| 3 | Pas d'horloge système dans `Rental` ni dans les services. Mais **`LocalDateTime.now(clock)` dépend du fuseau du `Clock`**. | Si quelqu'un remplace le bean par `Clock.systemUTC()`, le préavis est faux de 1 à 2 h. | Garde-fou : `ClockConfigurationTest` vérifie le fuseau. |
| 4 | **Une règle métier dupliquée hors du domaine** : la définition de « louable » est en SQL (`JdbcVehicleCatalog`) et dans `Vehicle.isRentable()`. La liste des statuts bloquants existe à trois endroits (`RentalStatus`, la contrainte V2, `JdbcVehicleCatalog`). | Une évolution d'un côté sans l'autre fait apparaître comme disponible un véhicule qu'on ne peut pas réserver, ou l'inverse. | Une vue SQL `rentable_vehicle` partagée, ou une colonne `rentable` calculée par `fleet` et lue par `rental`. Étendre le test `statuts_de_la_contrainte_alignes_sur_le_domaine` à `JdbcVehicleCatalog`. |
| 5 | Le listener journalise en ERROR et relance. Mais **un événement publié hors transaction est ignoré sans bruit** : `@TransactionalEventListener` sans `fallbackExecution` ne fait rien. | Si un futur cas d'usage publie hors `UnitOfWork`, le kilométrage n'est jamais mis à jour, sans aucun log. | Dans `SpringDomainEventPublisher`, refuser (ou journaliser en WARN) une publication sans transaction active. |
| 6 | Rejouer `VehicleReturned` deux fois : rien ne change (`recordReturnMileage` ignore un relevé égal ou inférieur, sans écriture en base). | — | — |
| 7 | Course rare entre le job d'expiration et une réservation qui expire la même ligne : l'une des deux transactions perd sur la version. | Le client reçoit un 409 `RENTAL_CONCURRENT_MODIFICATION` alors que le créneau était libre. | Dans `ReserveRentalService`, ignorer une réservation échue déjà expirée par un autre (recharger avant d'expirer), ou relancer une fois. |

---

## Clôture WE7 — Bilan de fin de phase 3

1. **Terminé :**
   - le cycle devis → réservation → confirmation → départ → retour → clôture,
     joué en HTTP sur l'application lancée et dans `RentalHttpJourneyTest` ;
   - le prix déterministe, prouvé par test ;
   - le kilométrage mis à jour par événement ;
   - les règles ArchUnit `rental` ↛ `fleet` ;
   - les ADR 0004 et 0005 ;
   - les tests du domaine (173) en **0,74 s**.
2. **À moitié fait :**
   - pas d'authentification ;
   - pas d'outbox ;
   - endpoints `fleet` partiels ;
   - invariant « pas de retrait de `FOR_RENT` avec des locations futures » non
     implémenté ;
   - les revues 4, 5 et 7 ci-dessus.
3. **Classe la plus complexe :** `Rental`, environ 450 lignes. Elle porte la
   machine à états, les données propres à chaque état, la cohérence à la
   relecture et la pénalité. Elle reste lisible parce que chaque transition
   est une méthode et que les règles de période sont dans `RentalPeriod`. Le
   prochain ajout devrait extraire la pénalité dans un objet dédié
   (`LateReturnPenalty`).
4. **Couplages qui gêneront `sales` (WE8) :**
   - `JdbcVehicleCatalog.java` lit les colonnes `status`, `for_rent` et
     `condition` de `vehicle`. `sales` devra s'appuyer sur `for_sale` et
     `SOLD` sans casser cette requête.
   - `Vehicle.dailyRentalRate` (`fleet`) est une donnée de location dans le
     parc. `sales` voudra un prix de vente : ne pas l'ajouter au même endroit.
   - `V2__fleet_and_rental.sql` contient la clé étrangère `rental.vehicle_id`
     → `vehicle`. Un véhicule vendu ne pourra jamais être supprimé, ce qui est
     voulu, mais c'est un lien de schéma entre contextes.
   - `VehicleReturnedListener.java` : `fleet` dépend des événements de
     `rental`. Pour `sales`, ce sera `fleet` qui écoutera `SaleOrderSigned`
     selon la même règle ArchUnit, à dupliquer pour `sales`.
   - `HexagonalArchitectureTest.java` : il faudra les règles `sales` ↛ `fleet`
     et `fleet` ↛ `sales` (sauf événements), à écrire **avant** `sales`.
5. **En entretien :**
   - pourquoi l'expiration ciblée à la réservation, et pas seulement le job ;
   - pourquoi l'arrondi par ligne ;
   - pourquoi le seuil de remise porte sur la durée réelle ;
   - pourquoi `REQUIRES_NEW` dans un listener `AFTER_COMMIT` ;
   - pourquoi un 409 sur un double-clic est « juste mais mauvais ».
