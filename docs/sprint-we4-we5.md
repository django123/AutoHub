# Sprint WE4 + WE5 — Notes : agrégat `Rental` et contrainte d'exclusion

Ce document consigne les réponses aux blocs « explique-moi » et « revue » de
`session-we4-we5-claude-code.md`. Le code a été généré plutôt qu'écrit à la
main. **Ces notes sont donc le support à relire avant l'entretien** : chaque
affirmation renvoie au fichier ou au test qui la démontre.

---

## Bloc 1.0 — État des lieux en début de sprint

- `mvn verify` : vert hors Testcontainers. Le seul échec venait de Docker
  Desktop, qui n'était pas lancé. Les 8 tests ArchUnit passaient.
- **Le WE3 (`Vehicle`) n'a jamais été fait.** Il n'y avait aucun code `fleet`.
  Ce sprint crée seulement `VehicleId` et une table `vehicle` minimale. L'agrégat
  `Vehicle` reste à écrire.
- **Le document de session parle de `tstzrange` et d'`Instant`, mais l'ADR 0002
  a tranché pour `tsrange` et `LocalDateTime`.** C'est l'ADR qui a été suivi.

---

## Bloc 1.1 — La frontière d'agrégat

**1. `Vehicle` ou `VehicleId` dans `Rental` ?** `VehicleId`.

- Avec l'objet, charger 100 locations charge 100 véhicules, en N+1 ou en
  jointure.
- Surtout, deux agrégats se retrouvent modifiables dans une même transaction :
  `rental.vehicle().updateMileage()` contourne les invariants de `Vehicle` et
  verrouille deux lignes au lieu d'une.
- Avec l'identifiant, un agrégat égale une transaction. La mise à jour du
  kilométrage passera par l'événement `VehicleReturned`, consommé par `fleet`.

**2. La non-superposition est-elle un invariant de `Rental` ?** Non.

Scénario : les threads T1 et T2 chargent l'état, ne voient aucune location sur
la période, créent chacun un `Rental` valide et l'insèrent. Rien, dans l'un ou
l'autre objet, ne permet de détecter l'autre. Un agrégat ne protège que ce
qu'il contient. Cet invariant traverse les agrégats : il est garanti par
`rental_no_overlap` (V2), et démontré par `RentalConcurrencyTest`.

**3. Où vit le prix ?** Il est figé à la création (`Rental.quotedPrice`).

- Calculé à la lecture, il changerait avec la grille tarifaire, et le client
  paierait autre chose que ce qu'il a accepté.
- Figé, il impose de stocker le montant et la devise (`quoted_amount`,
  `quoted_currency`), mais il est contractuel et auditable.

**4. `VehicleHandover` : entité ou value object ?** Value object.

C'est un constat figé, sans cycle de vie ni identité. Deux constats identiques
sont interchangeables. Il deviendra une entité le jour où il aura des photos
annotées et une signature corrigeable.

---

## Bloc 1.2 — Machine à états

Voir [ADR 0003](adr/0003-machine-a-etats-rental-enum.md).

---

## Bloc 1.4 — Revue critique de `Rental`

| # | Constat | Conséquence en production | Correction proposée |
|---|---|---|---|
| 1 | `Rental.restore()` vérifie la cohérence état/données, mais **pas les règles entre constats**. Une ligne `RETURNED` dont le kilométrage de retour est inférieur à celui du départ se recharge sans erreur. | Une correction SQL manuelle erronée passe inaperçue. | Ajouter `!dropoff.hasLowerMileageThan(pickup)` à `checkStateConsistency()`, ou un `CHECK (dropoff_mileage_km >= pickup_mileage_km)` en base. |
| 2 | Les accesseurs `status()`, `reservedUntil()`, `pickup()` sont publics, et nécessaires au mapper. | Un futur service peut écrire `if (rental.status() == RESERVED)` et sortir une règle de l'agrégat. | Les méthodes « Tell » existent (`expireIfOverdue`, `blocksVehicle`, `isLateReturn`). La vigilance se fait en revue. |
| 3 | Déméter : `rental.period().start()` dans le mapper. | Le couplage reste acceptable, puisqu'il est confiné à la persistance. | Rien. Le mapper est l'endroit où l'on a le droit de tout lire. |
| 4 | Pas de collection exposée. Les événements passent par `peekDomainEvents()`, en vue non modifiable. | — | — |
| 5 | Aucune séquence d'appels publics ne viole la machine à états (`RentalStatusTest` + `RentalTest`). `expireIfOverdue` contourne `ensureCanTransitionTo`, mais il est gardé par `status == RESERVED`. | — | — |
| 6 | Aucune règle métier dans les tests. | — | — |
| 7 | `Clock` injecté partout. **Mais `LocalDateTime.now(clock)` utilise le fuseau de l'horloge.** | Si le bean `Clock` est en UTC, le préavis est calculé avec 1 à 2 h d'erreur par rapport à l'heure nominale de l'agence. | Au WE6, déclarer `Clock.system(ZoneId.of("Europe/Paris"))`, et non `Clock.systemUTC()`. |
| 8 | Une réservation échue reste `RESERVED` en base **tant que personne n'appelle `expireIfOverdue`**. Elle continue de bloquer le véhicule pour la contrainte d'exclusion. | Après les 30 min, le véhicule reste indisponible jusqu'au passage du traitement d'expiration. | Le traitement planifié du WE7, avec son index partiel. |

**Quoi surveiller la prochaine fois :**

- Pour le n° 7 : tout `LocalDateTime.now(...)` doit faire se demander « dans quel
  fuseau ? ».
- Pour le n° 8 : toute règle temporelle doit faire se demander « qui fait
  avancer le temps ? ». Un état qui expire n'expire pas tout seul.

---

## Bloc 2.1 — GiST, à partir de zéro

**1. Pourquoi un B-tree ne peut pas indexer une plage.** Un B-tree exige un
ordre total : `<`, `=`, `>`. Or « chevauche » n'est pas un ordre. `[1,5)` chevauche
`[3,8)`, qui chevauche `[6,9)`, mais `[1,5)` ne chevauche pas `[6,9)` :
l'opérateur n'est même pas transitif.

```sql
SELECT tsrange('2027-01-01','2027-01-05') && tsrange('2027-01-03','2027-01-08'),  -- t
       tsrange('2027-01-03','2027-01-08') && tsrange('2027-01-06','2027-01-09'),  -- t
       tsrange('2027-01-01','2027-01-05') && tsrange('2027-01-06','2027-01-09');  -- f
```

**2. Ce que fait GiST.** C'est un arbre de « boîtes englobantes ». Chaque nœud
résume ses enfants par une plage qui les contient tous, et une recherche
élague les branches dont la boîte ne chevauche pas la cible. Il suffit que le
type sache répondre à « ces deux objets peuvent-ils se chevaucher ? ».

```sql
-- L'index de la contrainte est PARTIEL : la requete doit repeter son filtre
-- de statuts pour que le planificateur puisse l'utiliser.
SET enable_seqscan = off;  -- table presque vide : on force le choix pour l'observer
EXPLAIN SELECT * FROM rental
 WHERE vehicle_id = '11111111-1111-1111-1111-111111111111'
   AND period && tsrange('2027-03-09','2027-03-10')
   AND status IN ('RESERVED','CONFIRMED','PICKED_UP');
```

**3. Pourquoi `btree_gist`.** GiST ne sait pas, nativement, indexer l'égalité
d'un `uuid`. `btree_gist` ajoute des classes d'opérateurs GiST pour les types
scalaires, ce qui permet `vehicle_id WITH =` à côté de `period WITH &&` dans le
même index.

```sql
SELECT opcname FROM pg_opclass WHERE opcname = 'gist_uuid_ops';  -- n'existe qu'avec btree_gist
```

**4. Deux transactions concurrentes.** PostgreSQL écrit d'abord la ligne et
son entrée d'index, **puis** vérifie la contrainte d'exclusion.

- Si T2 vérifie après que T1 a inséré, T2 trouve la ligne non validée de T1 et
  **attend**. Si T1 valide, T2 échoue (`23P01`) ; si T1 annule, T2 réussit.
  L'échec survient **à l'INSERT**, pas au COMMIT.
- Si T1 et T2 ont **toutes deux** inséré avant de vérifier, chacune trouve la
  ligne de l'autre et l'attend. C'est un interblocage. Au bout de
  `deadlock_timeout` (1 s), PostgreSQL annule l'une des deux avec **`40P01`**,
  et l'autre poursuit.

Ce second cas n'était pas prévu. C'est le test à 50 threads qui l'a révélé, en
deux temps :

1. Il a échoué une fois sur deux avec des `CannotAcquireLockException` (`40P01`).
2. Une fois `40P01` traduit, il a encore échoué deux fois sur cinq, par
   épuisement du pool de connexions : chaque deadlock coûte 1 s, et 49 perdants
   sur 10 connexions dépassent les 30 s d'attente de Hikari.

**Correctif** : avant d'écrire une location bloquante, `RentalRepositoryAdapter`
prend `pg_advisory_xact_lock(<clé dérivée du vehicle_id>)`. Ce verrou
consultatif est libéré au COMMIT ou au ROLLBACK. Les écritures concurrentes sur
un même véhicule passent une par une, et chacune vérifie la contrainte contre
des lignes déjà validées : `23P01` immédiat, sans deadlock. Le test passe de
plus de 40 s à 3 s, 5 runs sur 5 au vert.

Le verrou **ne remplace pas** la contrainte. Il ordonne les transactions, mais
c'est toujours la contrainte qui refuse : sans elle, les 50 réservations
passent, ce qui a été revérifié après l'ajout du verrou. `40P01` reste traduit
en `VehicleNotAvailableException` par sécurité, pour une écriture qui ne
passerait pas par l'adaptateur.

Deux sessions `psql` permettent de le constater :

```sql
-- session A
BEGIN; INSERT INTO rental ... ('2027-03-08 08:00','2027-03-10 08:00','CONFIRMED' ...);
-- session B (reste bloquée)
BEGIN; INSERT INTO rental ... ('2027-03-09 08:00','2027-03-11 08:00','RESERVED' ...);
-- session A
COMMIT;   -- B se débloque immédiatement avec ERROR 23P01
```

---

## Bloc 2.2 — Le mapping de la plage (vérifié dans psql, PostgreSQL 16)

| Question | Résultat constaté |
|---|---|
| `tsrange(timestamp, timestamp, text)` est-il `IMMUTABLE` ? | Oui (`provolatile = 'i'`), comme `tstzrange`. |
| (b) Colonne générée `STORED` + `EXCLUDE` sur elle ? | Acceptée. Le chevauchement est rejeté, l'adjacence passe. |
| (c) `EXCLUDE` sur l'expression `tsrange(s, e, '[)')` ? | Acceptée aussi, avec le même comportement. |

**Option retenue : (b).** L'entité JPA ne voit que deux `LocalDateTime`, ce qui
exclut (a). Entre (b) et (c), (b) donne une colonne `period` lisible dans `psql`
et utilisable telle quelle dans la future requête de disponibilité. Avec (c),
chaque requête devrait recopier l'expression exacte pour que le planificateur
utilise l'index. Le coût de (b) est de stocker la plage en double, ce qui est
négligeable.

**(a), le `UserType`**, aurait fait remonter un type `Range<LocalDateTime>` dans
l'entité et le mapper, pour aucun bénéfice : JPA n'interroge jamais la plage.

### Le piège découvert en chemin : le fuseau de la JVM

Les deux colonnes simples ne suffisent pas à rendre l'ADR 0002 vrai.
`application.yml` contenait `hibernate.jdbc.time_zone: UTC`, et dans ce cas
Hibernate convertit chaque `LocalDateTime` en `java.sql.Timestamp` **dans le
fuseau de la JVM**, puis l'écrit en UTC. Sur une JVM `Europe/Paris`, on a
mesuré :

- `08:00` stocké `07:00`, ce que révèle `RentalRepositoryAdapterTest.heures_nominales_en_base` ;
- `02:00 → 03:00` le 28 mars 2027, stocké `01:00 → 01:00`. La plage est **vide**,
  donc elle échappe à la contrainte d'exclusion. C'est exactement le scénario
  de l'ADR 0002, réintroduit par la couche JDBC. Seul le `CHECK rental_period_not_empty`
  l'a arrêté.

Correctif : `hibernate.type.java_time_use_direct_jdbc: true`. Le `LocalDateTime`
est alors passé tel quel au pilote. Comme les runners de CI sont en UTC, où le
bug est invisible, la JVM de test de `autohub-bootstrap` est forcée en
`Europe/Paris` (`pom.xml`, `argLine`).

---

## Bloc 2.3 — Preuve à la main dans psql

Rejouée sur une base jetable où V1 et V2 sont appliquées :

```sql
INSERT INTO vehicle (id, vin, plate_number, brand, model, model_year, mileage_km, status)
VALUES ('11111111-1111-1111-1111-111111111111','VF3ABCDEF12345678','AA-123-BB','Peugeot','208',2024,10000,'IN_FLEET');

-- référence : lundi 08:00 -> mercredi 08:00
INSERT INTO rental (id, vehicle_id, customer_id, starts_at, ends_at, status, quoted_amount, quoted_currency)
VALUES (gen_random_uuid(),'11111111-1111-1111-1111-111111111111',gen_random_uuid(),
        '2027-03-08 08:00','2027-03-10 08:00','CONFIRMED',180,'EUR');                -- INSERT 0 1

-- chevauchement : mardi -> jeudi
INSERT INTO rental (...) VALUES (..., '2027-03-09 08:00','2027-03-11 08:00','RESERVED', ...);
-- ERROR: 23P01: conflicting key value violates exclusion constraint "rental_no_overlap"

-- adjacente : mercredi 08:00 -> vendredi
INSERT INTO rental (...) VALUES (..., '2027-03-10 08:00','2027-03-12 08:00','CONFIRMED', ...);  -- INSERT 0 1

-- chevauchante mais annulée
INSERT INTO rental (...) VALUES (..., '2027-03-09 08:00','2027-03-11 08:00','CANCELLED', ...);  -- INSERT 0 1
```

---

## Bloc 2.4 — Le test de concurrence

1. **`ExecutorService` + 50 `submit()` ne suffit pas.** Les threads démarrent
   les uns après les autres, et le premier peut avoir validé avant que le
   dixième existe. On ajoute un `CountDownLatch` `ready` (tous prêts) et un
   `CountDownLatch` `startGun` (top départ commun).
2. **SQLState** : `23P01` (`exclusion_violation`). Une clé unique donnerait
   `23505`, une clé étrangère `23503`. **Sous forte concurrence, une partie
   des perdants reçoit `40P01` (deadlock)** au lieu de `23P01` (voir bloc 2.1,
   point 4). Spring l'enveloppe alors en `CannotAcquireLockException`, et non en
   `DataIntegrityViolationException`.
3. **Enveloppes successives** : `PSQLException` → Hibernate
   `ConstraintViolationException` → Spring `DataIntegrityViolationException`.
4. **Où traduire** : dans `RentalRepositoryAdapter`, qui parcourt la chaîne des
   causes jusqu'à la `SQLException`. Surtout pas dans le domaine, qui ne
   connaît ni JDBC ni Spring : il déclare `VehicleNotAvailableException` mais
   ne la lève jamais. La traduction exige un `saveAndFlush`, sans quoi l'INSERT
   partirait au COMMIT, hors de l'adaptateur.
5. **Pourquoi pas H2** : H2 ne connaît ni `tsrange` ni `EXCLUDE USING gist`.

Résultat : 1 succès, 49 `VehicleNotAvailableException`. **Sans la contrainte :
50 succès.** C'est vérifié en la retirant temporairement de V2.

---

## Bloc 2.5 — Revue du mapper et de l'adaptateur

1. **Fuite domaine ↔ JPA** : aucune. Le module `domain` ne voit pas
   `jakarta.persistence`. L'entité stocke le statut en `String` et non en
   `RentalStatus`. Une nouvelle règle ArchUnit interdit à toute classe hors de
   `..persistence..` de dépendre d'une `@Entity`.
2. **Reconstruction** : elle passe par les constructeurs des value objects
   (`RentalPeriod`, `Money`, `VehicleHandover`), qui revalident, puis par
   `Rental.restore()`, qui ne rejoue pas l'historique.
   - Rejouer les transitions reverifierait le préavis aujourd'hui, et une
     location d'hier deviendrait illisible : c'est un bug.
   - Tout contourner laisserait passer des données corrompues.
   - `restore()` vérifie donc la cohérence état/données, sans les règles qui
     dépendent du moment. Limite : voir la revue 1.4, n° 1.
3. **`version`** : le champ vit dans l'entité JPA seulement, ce qui garde le
   domaine pur. La contrepartie : le verrouillage optimiste ne protège que si
   le chargement et la sauvegarde ont lieu dans la même transaction. `save` est
   donc en `Propagation.MANDATORY`, et `mise_a_jour_perdue_evitee` le démontre.
   L'alternative, mettre `version` dans l'agrégat, le rend correct entre deux
   requêtes HTTP, au prix d'un détail de stockage dans le domaine.
4. **Exceptions qui fuient encore** :
   - `OptimisticLockingFailureException` (Spring) remonte telle quelle vers
     l'application ;
   - `IllegalStateException` remonte si une ligne est incohérente ;
   - `DataIntegrityViolationException` remonte pour toute autre violation.

   La première mérite une exception de domaine (`ConcurrentRentalModificationException`)
   avant le WE6, sinon l'API répondra 500 au lieu de 409.
   *(Fait le 2026-10-12 : l'adaptateur la traduit, et `mise_a_jour_perdue_evitee`
   le vérifie.)*

---

## Clôture — Bilan critique

**1. Réellement terminé**

- `Rental` et sa machine à états, testés sans Spring (77 tests pour `rental`,
  104 pour tout le domaine, moins de 2 s).
- La migration V2 et sa contrainte d'exclusion partielle, prouvée dans psql.
- L'adaptateur JPA : verrou consultatif par véhicule, traduction `23P01` / `40P01` → `VehicleNotAvailableException`.
- Le test à 50 threads, vérifié en échec sans la contrainte.
- Les tests de l'ADR 0002 (heures nominales, passage à l'heure d'été), et la
  correction du binding JDBC qu'ils ont révélée.
- L'ADR 0003.

**2. À moitié fait, et piégeux au prochain week-end**

- **`Vehicle` (WE3) n'existe pas.** La table `vehicle` est une maquette.
- **Aucun cas d'usage.** Personne n'appelle encore `pullDomainEvents()` ni
  `EventPublisher`.
- **Les réservations échues bloquent le véhicule** jusqu'au traitement
  d'expiration (WE7).
- **Le bean `Clock` devra être dans le fuseau de l'agence.**
- **`OptimisticLockingFailureException` n'est pas traduite.** *(Fait le 2026-10-12 : `ConcurrentRentalModificationException`.)*
- **L'ADR 0002 est toujours « proposé »**, alors qu'il est désormais implémenté
  et testé. Il faut le passer à « accepté ». *(Fait le 2026-10-12.)*

**3. ArchUnit** : toujours vert. La règle 3 n'est plus vide (`allowEmptyShould`
retiré), et une règle a été ajoutée : seule la persistance manipule les
entités JPA.

**4. Ce qui mettrait en difficulté en entretien**

- Pourquoi `java_time_use_direct_jdbc`. Il faut savoir raconter la chaîne
  `LocalDateTime` → `Timestamp` → fuseau JVM → UTC.
- Pourquoi `Propagation.MANDATORY`, et ce qui se passerait sans.
- Pourquoi la contrainte est partielle, et ce que cela implique pour une
  réservation échue mais non expirée.
- Pourquoi l'exception est déclarée dans le domaine mais levée par
  l'infrastructure.
- Pourquoi une contrainte d'exclusion peut provoquer un **deadlock**, et
  pourquoi le verrou consultatif l'évite sans se substituer à la contrainte.
