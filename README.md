
# AutoHub

Plateforme de **location et vente de véhicules**, construite en Java 21 /
Spring Boot selon une architecture hexagonale stricte.

> **Nature du projet.** AutoHub est un projet d'apprentissage assumé. Il est
> volontairement sur-architecturé pour son périmètre fonctionnel : quatre
> modules Maven et un découpage DDD pour gérer une flotte de véhicules, c'est
> disproportionné dans l'absolu. C'est justifié ici parce que l'objectif est de
> pratiquer ces techniques sur un domaine assez riche pour qu'elles aient du
> sens. Ce README explique systématiquement **pourquoi** chaque choix a été
> fait, pas seulement **quoi** a été fait.

---

## Démarrage rapide

```bash
# 1. Lancer PostgreSQL
docker compose up -d

# 2. Compiler et tester (nécessite Docker pour Testcontainers)
mvn verify

# 3. Publier les modules dans le dépôt local, puis lancer l'application
#    Le `install` n'est pas facultatif : `-pl` réduit le réacteur Maven à un seul
#    module, qui doit alors résoudre autohub-infrastructure depuis ~/.m2 —
#    or `verify` n'y publie rien. À rejouer après toute modification d'un
#    module amont.
mvn install -DskipTests
mvn -pl autohub-bootstrap spring-boot:run

# 4. Vérifier
curl http://localhost:8080/actuator/health
```

> **Port HTTP déjà pris ?** Sur la machine de développement, 8080 est occupé.
> Lancer avec `mvn -pl autohub-bootstrap spring-boot:run -Dspring-boot.run.arguments=--server.port=8085`.

### Jouer un parcours complet en HTTP

```bash
B=http://localhost:8080; J='Content-Type: application/json'

# Un véhicule : ajout, préparation, mise en service
curl -X POST $B/api/vehicles -H "$J" -d '{"vin":"VF1RJA00012345678","plateNumber":"GH-417-KL",
  "brand":"Renault","model":"Clio","year":2024,"mileageKm":12000,"condition":"NEW",
  "offerings":["FOR_RENT"],"dailyRentalRate":45.00,"currency":"EUR"}'
curl -X POST $B/api/vehicles/{id}/preparation
curl -X POST $B/api/vehicles/{id}/commissioning

# Devis : véhicules libres sur la période, prix détaillé ligne par ligne
curl "$B/api/vehicles/available?start=2026-10-20T08:00&end=2026-10-28T08:00"

# Réserver (expire sous 30 min), confirmer, partir, rendre, clore
curl -X POST $B/api/rentals -H "$J" -d '{"vehicleId":"{id}","customerId":"…",
  "start":"2026-10-20T08:00:00","end":"2026-10-28T08:00:00"}'
curl -X POST $B/api/rentals/{rentalId}/confirm
curl -X POST $B/api/rentals/{rentalId}/pickup -H "$J" -d '{"at":"2026-10-20T09:00:00","mileageKm":12000,"fuelLevelPercent":100}'
curl -X POST $B/api/rentals/{rentalId}/return -H "$J" -d '{"at":"2026-10-28T10:30:00","mileageKm":13150,"fuelLevelPercent":30}'
curl -X POST $B/api/rentals/{rentalId}/close

# Le kilométrage du véhicule a suivi le retour, par événement
curl $B/api/vehicles/{id}
```

Ce parcours a été joué sur l'application lancée. Résultats obtenus : un devis
de 324 € (8 jours à 45 €, remise de 10 %), un 409 sur une seconde réservation du
même créneau, une pénalité de 67,50 € pour 2 h 30 de retard, et un kilométrage
du véhicule passé à 13 150 km après le retour.

> **Port de la base : 15432, pas 5432.** Un service PostgreSQL natif peut déjà
> occuper 5432 sur la machine de développement — et sous Windows, deux serveurs
> peuvent écouter le même port sans qu'aucun ne proteste, la connexion partant
> alors vers l'un ou l'autre au hasard. Le port publié par `docker-compose.yml`
> est donc dédié. Le port *interne* au réseau Compose reste 5432.

**Prérequis** : JDK 21+, Maven 3.9+, Docker.

Pour lancer la stack d'observabilité (Prometheus + Grafana) :

```bash
docker compose --profile observability up -d
# Grafana : http://localhost:3000 (admin / admin)
```

---

## 1. Pourquoi l'architecture hexagonale

L'architecture en couches classique (`controller` → `service` → `repository`)
a un défaut structurel : **les dépendances pointent vers la base de données**.
Le métier finit par dépendre de JPA, et donc épouser ses contraintes —
constructeur vide, setters partout, collections mutables. On obtient un
*anemic domain model* : des sacs de getters/setters, et toute la logique
métier dispersée dans des services de plusieurs centaines de lignes.

L'architecture hexagonale inverse la flèche : **tout pointe vers le domaine**.

```
        ┌─────────────────────────────────────────────┐
        │            INFRASTRUCTURE                   │
        │   REST · JPA · Kafka · clients HTTP         │
        │                                             │
        │   ┌─────────────────────────────────────┐   │
        │   │          APPLICATION                │   │
        │   │   cas d'usage · ports (interfaces)  │   │
        │   │                                     │   │
        │   │   ┌─────────────────────────────┐   │   │
        │   │   │         DOMAINE             │   │   │
        │   │   │  agrégats · value objects   │   │   │
        │   │   │  invariants · événements    │   │   │
        │   │   │                             │   │   │
        │   │   │  ZÉRO dépendance externe    │   │   │
        │   │   └─────────────────────────────┘   │   │
        │   └─────────────────────────────────────┘   │
        └─────────────────────────────────────────────┘

            Les dépendances vont toujours vers l'intérieur.
```

### La preuve tient en un fichier

Ouvre [`autohub-domain/pom.xml`](autohub-domain/pom.xml). Sa section
`<dependencies>` est **vide**.

C'est la démonstration la plus courte que l'architecture est respectée. Pas de
Spring, pas de JPA, pas de Jackson, pas de Lombok. Le domaine est du Java
ordinaire, et cela a trois conséquences concrètes :

1. Ses tests s'exécutent en **millisecondes** — donc on les lance en continu,
   pas une fois par jour.
2. Il est **portable** : réutilisable dans un batch, une Lambda, une app
   Quarkus, sans modification.
3. Aucune décision technique ne peut **fuir** dans les règles métier.

---

## 2. Les modules

| Module | Rôle | Dépendances |
|---|---|---|
| `autohub-domain` | Agrégats, value objects, invariants, événements | **aucune** |
| `autohub-application` | Cas d'usage, ports (interfaces) | `domain` |
| `autohub-infrastructure` | Adaptateurs : REST, JPA, publication d'événements | `application` + Spring |
| `autohub-bootstrap` | Assemblage, configuration, tests transverses | `infrastructure` |

Le découpage en modules Maven n'est pas décoratif : **le compilateur refuse
une dépendance inversée**. Si tu écris `import org.springframework...` dans le
domaine, le build échoue. Ce n'est pas une convention qu'on peut oublier un
vendredi soir, c'est une impossibilité.

---

## 3. Concepts du domaine

### 3.1 Value Object vs Entité

C'est la distinction la plus importante du DDD tactique, et la plus souvent
mal comprise.

| | Value Object | Entité / Agrégat |
|---|---|---|
| **Identité** | aucune — défini par son contenu | un `id` stable dans le temps |
| **Égalité** | par valeur | par identité |
| **Mutabilité** | immuable | évolue au fil de sa vie |
| **Exemples** | `Money`, `DateRange`, `Vin` | `Vehicle`, `Rental` |

Le test qui tranche : *si je change un attribut, est-ce toujours la même
chose ?*

- 10 € dont je change le montant en 20 € : ce n'est plus le même montant →
  **value object**.
- Une voiture dont le kilométrage passe de 10 000 à 15 000 : c'est toujours
  la même voiture → **entité**.

### 3.2 Tell-Don't-Ask

Comparons deux façons d'écrire la même chose :

```java
// ❌ ASK : on interroge l'objet, puis on décide à sa place
if (rental.getStatus() == RentalStatus.RESERVED
        && rental.getReservedUntil().isBefore(Instant.now())) {
    rental.setStatus(RentalStatus.EXPIRED);
}

// ✅ TELL : on demande à l'objet d'agir, il décide lui-même
rental.expireIfOverdue(clock);
```

La première version répartit la règle métier chez **chaque appelant** : le
jour où elle change, il faut retrouver tous les endroits qui l'ont recopiée.
La seconde la garde à un seul endroit, celui qui détient les données.

**Symptôme diagnostique** : si tu écris un `getX()` uniquement pour prendre
une décision juste après, la décision appartient probablement à l'objet.

### 3.3 Loi de Déméter

> Ne parle qu'à tes amis immédiats.

```java
// ❌ On traverse trois objets — chaque point est un couplage
BigDecimal montant = rental.getQuote().getTotal().getAmount();

// ✅ Un seul niveau
Money total = rental.totalDue();
```

La première version casse dès que `Quote` change de structure interne. C'est
exactement ce que fait `Rental` : le devis (`RentalQuote`) est interne, et
l'agrégat expose `quotedPrice()`, `dailyRate()` et `totalDue()`.
Règle pratique : **un seul point** dans une chaîne d'appels (les fluent APIs
et les streams font exception).

### 3.4 Invariants protégés à la construction

Un invariant est une règle qui doit être vraie **à tout instant**, pas
seulement au moment où on pense à la vérifier.

```java
public record DateRange(LocalDateTime start, LocalDateTime end) {
    public DateRange {
        BusinessRuleViolation.check(
                end.isAfter(start),
                "DATE_RANGE_INVALID",
                "La fin doit être strictement postérieure au début");
    }
}
```

La conséquence est plus forte qu'il n'y paraît : **il est impossible de tenir
en main un `DateRange` invalide**. Aucune vérification défensive n'est
nécessaire ailleurs dans le code. C'est le principe *« make illegal states
unrepresentable »*.

### 3.5 Trois familles de refus : invalide, en conflit, introuvable

Le domaine ne connaît pas HTTP. Il distingue pourtant trois catégories de
refus, parce qu'un appelant ne réagit pas de la même façon à chacune :

| | Demande **invalide** | Demande **en conflit** avec l'état actuel | Ressource **introuvable** |
|---|---|---|---|
| Exemple | location d'une demi-heure, VIN mal formé | véhicule déjà loué, location déjà annulée, modification concurrente | location ou véhicule inconnu |
| La même demande, plus tard | échouera toujours | peut réussir après avoir rechargé l'état | échouera tant que l'identifiant est faux |
| Réaction attendue | corriger sa saisie | recharger et reconsidérer | vérifier l'identifiant |
| Classe du domaine | `BusinessRuleViolation` | sous-classe de `ConflictException` | sous-classe de `NotFoundException` |
| Traduction REST | `422 Unprocessable Entity` | `409 Conflict` | `404 Not Found` |

`ConflictException` et `NotFoundException` sont des catégories métier, pas
des codes HTTP déguisés. C'est `GlobalExceptionHandler` qui choisit le statut.
Comme Spring retient toujours le handler le plus spécifique, une nouvelle
exception n'a qu'à hériter de la bonne catégorie : il n'y a aucune liste à
tenir à jour, donc aucun oubli possible.

---

## 4. Le piège du `BigDecimal`

Sujet apparemment anodin, qui mérite qu'on s'y arrête.

```java
new BigDecimal("2.50").equals(new BigDecimal("2.5"));     // false !
new BigDecimal("2.50").compareTo(new BigDecimal("2.5"));  // 0
```

`equals` compare aussi l'**échelle** (le nombre de décimales). Comme `Money`
est un `record`, son `equals` généré délègue à celui de `BigDecimal` et
hériterait du problème : deux montants économiquement identiques seraient
considérés différents.

La parade est dans le constructeur compact, qui normalise systématiquement
l'échelle sur celle de la devise :

```java
amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
```

Et bien sûr, **jamais de `double`** pour de l'argent : `0.1 + 0.2` vaut
`0.30000000000000004`.

---

## 5. Le choix décisif : intervalles semi-ouverts

`DateRange` représente `[start, end)` — début **inclus**, fin **exclue**.

Ce n'est pas un détail. Considère deux locations qui s'enchaînent :

- Location A : lundi 08:00 → mercredi 08:00
- Location B : mercredi 08:00 → vendredi 08:00

Un client rend la voiture, un autre la prend au même moment. Scénario
parfaitement normal.

Avec des intervalles **fermés** `[start, end]`, ces deux locations se
chevauchent mercredi à 08:00. Il faudrait écrire partout des
`end.minusSeconds(1)` — laid, dépendant de la précision du stockage, et
source de bugs qui n'apparaissent qu'en production.

Avec des intervalles **semi-ouverts**, A se termine juste avant que B ne
commence. Aucun chevauchement, aucune soustraction arbitraire.

**Le bénéfice décisif arrive au week-end 5** : c'est exactement la sémantique
des types range de PostgreSQL, notée `'[)'`. Notre modèle Java et notre
contrainte d'exclusion GiST partagent la même définition du chevauchement.
Sans cela, le code et la base se contrediraient sur les cas limites — le pire
type de bug qui soit.

Le type retenu est `tsrange` (sans fuseau) et non `tstzrange` : la nuit du
passage à l'heure d'été, une plage `tstzrange` peut devenir vide et échapper à
la contrainte. Mesures et décision dans
[ADR 0002](docs/adr/0002-tsrange-plutot-que-tstzrange.md).

---

## 6. ArchUnit : rendre les règles impossibles à violer

[`HexagonalArchitectureTest`](autohub-bootstrap/src/test/java/com/ocarius/autohub/architecture/HexagonalArchitectureTest.java)
encode les règles architecturales (douze `@ArchTest`). Une violation fait
échouer la CI.

```java
@ArchTest
static final ArchRule le_domaine_ne_depend_d_aucun_framework = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..");
```

**Pourquoi dès le week-end 1, avant qu'il n'y ait rien à protéger ?**

Parce qu'une règle architecturale ajoutée après coup échoue sur cinquante
violations existantes, et finit invariablement commentée avec un
`// TODO à réactiver`. Posée en premier, elle n'a jamais l'occasion d'être
violée.

Les règles d'isolation entre contextes (`rental` ne dépend pas de `sales`)
ont été posées avant que ces modules n'existent. C'est exactement le but :
**la barrière est posée avant la tentation**.

Au week-end 5, avec la première entité JPA, une règle a été ajoutée : **seul le
paquet `..persistence..` peut dépendre d'une `@Entity`**. Sans elle, un
contrôleur pourrait un jour sérialiser directement `RentalJpaEntity`, et le
schéma de la base deviendrait de fait le contrat de l'API.

Aux week-ends 6 et 7, trois règles de plus :

- **Les contrôleurs ne voient que des ports** : rien dans `..infrastructure.in.rest..`
  ne dépend du domaine ni d'un service d'application.
- **`rental` ne dépend pas de `fleet`**, et **`fleet` ne dépend de `rental` que
  par ses événements** (ADR 0004). Elles ont été écrites *avant* le code qui
  met à jour le kilométrage au retour, c'est-à-dire au moment exact où la
  tentation d'appeler `VehicleRepository` depuis `rental` apparaît. On a vérifié
  qu'elles mordent, en introduisant volontairement la dépendance interdite.

---

## 7. Choix de persistance

### Flyway, pas `ddl-auto=update`

Trois raisons :

1. `ddl-auto` **dérive le schéma du modèle Java**. On subit le schéma au lieu
   de le concevoir. Or les fonctionnalités PostgreSQL qui font l'intérêt de ce
   projet (`tstzrange`, `EXCLUDE USING gist`) sont **inexprimables en JPA**.
2. Un schéma versionné est rejouable à l'identique en local, en CI et en
   production. `ddl-auto` ne l'est pas.
3. `ddl-auto` ne sait pas écrire une migration de données.

La configuration est donc `ddl-auto: validate` : Hibernate vérifie que le
schéma correspond au modèle, mais ne le modifie jamais.

> **Règle absolue** : une migration livrée n'est **jamais** modifiée. On
> corrige en ajoutant `V2`, `V3`... Flyway stocke une empreinte de chaque
> fichier et refusera de démarrer si l'un d'eux a changé.

### `open-in-view: false`

Désactive l'anti-pattern OSIV. Par défaut, Spring garde la session Hibernate
ouverte jusqu'au rendu de la réponse, ce qui masque les chargements paresseux
et provoque des N+1 invisibles. Avec `false`, une `LazyInitializationException`
surgit **en test** plutôt qu'une lenteur inexplicable en production.

### Testcontainers, pas H2

H2 démarre plus vite, et c'est son seul avantage. Il ne connaît ni
`tstzrange`, ni `EXCLUDE USING gist`, ni `JSONB` — exactement les
fonctionnalités sur lesquelles repose ce projet.

Tester sur un moteur différent de celui de production, c'est tester autre
chose que ce qu'on livre.

### La double réservation rendue physiquement impossible

La non-superposition des locations n'est **pas** un invariant de l'agrégat
`Rental`. Un agrégat ne voit que lui-même : deux threads qui créent chacun une
location valide sur la même période n'ont aucun moyen de se connaître. Un
`SELECT` de vérification suivi d'un `INSERT` laisse une fenêtre entre les deux.
L'invariant traverse les agrégats, donc il appartient à la base
([`V2__fleet_and_rental.sql`](autohub-infrastructure/src/main/resources/db/migration/V2__fleet_and_rental.sql)) :

```sql
period tsrange GENERATED ALWAYS AS (tsrange(starts_at, ends_at, '[)')) STORED,

CONSTRAINT rental_no_overlap EXCLUDE USING gist (
    vehicle_id WITH =,
    period     WITH &&
) WHERE (status IN ('RESERVED', 'CONFIRMED', 'PICKED_UP'))
```

- **GiST** : un B-tree ne sait indexer qu'un ordre total, et « chevauche » n'en
  est pas un. **`btree_gist`** permet de combiner l'égalité sur `uuid` avec le
  chevauchement de plages dans le même index.
- **Colonne générée** : l'entité JPA ne voit que `starts_at` et `ends_at`, deux
  `LocalDateTime` ordinaires. PostgreSQL calcule la plage lui-même. Aucun type
  exotique ne remonte dans l'entité ni dans le mapper.
- **Contrainte partielle** : une location annulée, expirée ou rendue libère le
  véhicule. La liste des statuts est dupliquée dans `RentalStatus.blockingStatuses()`,
  et un test d'intégration compare les deux en lisant `pg_constraint`.
- **Traduction d'exception** : `RentalRepositoryAdapter` repère le SQLState
  `23P01` et lève `VehicleNotAvailableException`, une exception du domaine. Le
  domaine la déclare mais ne la lève jamais, puisqu'il ignore que PostgreSQL
  existe. L'adaptateur fait un `saveAndFlush` pour que la violation surgisse
  chez lui, et non au COMMIT, hors de sa portée.
- **Verrou consultatif par véhicule** : PostgreSQL insère la ligne *avant* de
  vérifier la contrainte, donc deux réservations concurrentes peuvent
  s'attendre mutuellement (deadlock, `40P01`, 1 s de pénalité chacun). Le test à
  50 threads l'a révélé. L'adaptateur prend `pg_advisory_xact_lock` sur
  l'identifiant du véhicule avant d'écrire : les réservations d'un même
  véhicule passent une par une, et la contrainte les refuse immédiatement. Le
  verrou ordonne les transactions, mais c'est toujours la contrainte qui
  décide.

La preuve est [`RentalConcurrencyTest`](autohub-bootstrap/src/test/java/com/ocarius/autohub/rental/infrastructure/out/persistence/RentalConcurrencyTest.java) :
50 threads libérés au même instant par un `CountDownLatch` réservent le même
véhicule sur la même période. Résultat : **1 succès, 49 refus**. Sans la
contrainte, on obtient 50 succès. C'est vérifié : un test qui ne peut pas
échouer ne teste rien.

### Entité JPA ≠ agrégat

`RentalJpaEntity` est un sac de colonnes : constructeur vide, statut en
`String` et non en enum du domaine, aucune logique. `RentalMapper`, écrit à la
main, reconstruit l'agrégat via les constructeurs des value objects, qui
revalident, puis via `Rental.restore()`. Celui-ci vérifie la cohérence entre
l'état et ses données, sans rejouer l'historique : rejouer `reserve()`
revérifierait le préavis aujourd'hui, et une location d'hier deviendrait
illisible.

Le champ `version` du verrouillage optimiste vit **uniquement dans l'entité**.
Pour qu'il protège quelque chose, le chargement et la sauvegarde doivent avoir
lieu dans la même transaction. `RentalRepository.save()` est donc en
`Propagation.MANDATORY` : appelé hors transaction, il échoue au lieu de perdre
silencieusement une modification concurrente. Quand l'`UPDATE ... WHERE version = ?`
ne trouve plus sa ligne, l'adaptateur traduit l'`OptimisticLockingFailureException`
de Spring en `ConcurrentRentalModificationException`, une exception du domaine.
La couche application n'a ainsi aucune exception Spring à connaître.

### `LocalDateTime` et fuseau horaire : le piège JDBC

`hibernate.jdbc.time_zone: UTC` fait convertir par Hibernate chaque
`LocalDateTime` en `Timestamp`, dans le fuseau de la JVM, puis l'écrit en UTC.
Les tests d'intégration ont révélé deux effets sur une JVM `Europe/Paris` :

- `08:00` est stocké `07:00` ;
- la plage `02:00 → 03:00` du passage à l'heure d'été devient `01:00 → 01:00`.
  Elle est vide, et échappe à la contrainte d'exclusion.

C'est le défaut de l'ADR 0002, réintroduit par la couche JDBC. Le correctif est
`hibernate.type.java_time_use_direct_jdbc: true`. Les runners de CI tournant en
UTC, où le bug est invisible, la JVM de test est forcée en `Europe/Paris`
(`autohub-bootstrap/pom.xml`).

### Une réservation échue ne gèle pas le créneau

La contrainte filtre sur le **statut stocké**. Une réservation non confirmée
depuis plus de 30 minutes reste `RESERVED` en base tant que personne ne l'a
fait expirer, et elle bloquerait le créneau. Deux mécanismes se complètent :

- **à la réservation**, `ReserveRentalService` fait expirer, dans la même
  transaction, les réservations échues qui chevauchent la période demandée.
  Le créneau se libère donc exactement quand quelqu'un le demande ;
- **un traitement planifié** (`ReservationExpiryScheduler`, toutes les minutes)
  fait passer les autres à `EXPIRED` et publie `RentalExpired`. Il est servi par
  l'index partiel `rental_overdue_reservation_idx` (V3).

La recherche de disponibilités ignore elle aussi les réservations échues.
Comparaison avec l'expiration paresseuse et `pg_cron` : `docs/sprint-we6-we7.md`,
bloc 6.2.

---

## 8. Cas d'usage, transactions et événements

### Un cas d'usage, une classe

`ReserveRentalService`, `ConfirmRentalService`… : chaque cas d'usage implémente
un **port entrant** (`ReserveRentalUseCase`) et ne reçoit que ses propres
dépendances. Un contrôleur injecte l'interface, jamais l'implémentation. Les
services sont des classes Java ordinaires, instanciées par l'infrastructure
(`RentalUseCaseConfiguration`) : la couche application n'a pas Spring.

Ils sont testés **sans Spring ni Mockito** (`RentalUseCasesTest`,
`FleetUseCasesTest`), avec des fakes en mémoire de quelques dizaines de lignes.
Si un cas d'usage demandait Mockito, ce serait le signe de ports trop gros.

### La transaction est une décision du cas d'usage

C'est le cas d'usage qui sait quelles étapes doivent réussir ensemble, par
exemple « expirer la réservation échue » et « enregistrer la nouvelle ». Faute
de Spring dans la couche application, il délimite sa transaction par un port,
`UnitOfWork`, que l'infrastructure implémente avec un `TransactionTemplate`
(**ADR 0005**). Les repositories exigent une transaction existante
(`Propagation.MANDATORY`) et n'en ouvrent jamais.

### Publier dans la transaction, délivrer après le commit

Le cas d'usage remet les événements de l'agrégat à l'`EventPublisher` juste
après le `save`. `SpringDomainEventPublisher` les pose sur le bus Spring, et
les consommateurs déclarés `@TransactionalEventListener(AFTER_COMMIT)` ne les
reçoivent qu'**après un commit réussi**. Publier réellement avant le commit
serait un bug : un rollback laisserait un consommateur agir sur un fait qui
n'a jamais eu lieu.

### Entre contextes : par événement, jamais par appel

Au retour d'un véhicule, son kilométrage doit suivre dans `fleet`. `rental`
n'appelle pas `fleet` : il publie `VehicleReturned`, et `VehicleReturnedListener`
(dans `fleet`) met à jour le véhicule après le commit, dans sa propre
transaction (**ADR 0004**). Si cette mise à jour échoue, le retour reste
valide, ce qui est voulu, et l'échec est journalisé en ERROR. Le consommateur
est idempotent : un événement rejoué ou arrivé en retard ne change rien.

Pour lire le parc (le véhicule est-il louable ? à quel tarif ?), `rental` a son
propre port, `VehicleCatalog`, implémenté en SQL : c'est une couche
anti-corruption, qui n'importe aucune classe de `fleet`. Seul `VehicleId` est
partagé, dans le noyau commun.

---

## 9. API REST

| Méthode | Chemin | Rôle | Réponses |
|---|---|---|---|
| `POST` | `/api/vehicles` | enregistrer un véhicule acheté | 201, 409 (VIN ou plaque en double), 422 |
| `POST` | `/api/vehicles/{id}/preparation` | commencer la préparation | 200, 404, 409 |
| `POST` | `/api/vehicles/{id}/commissioning` | mettre en service | 200, 404, 409, 422 (endommagé) |
| `GET` | `/api/vehicles/{id}` | consulter un véhicule | 200, 404 |
| `GET` | `/api/vehicles/available?start&end` | disponibilités et devis | 200, 422 |
| `POST` | `/api/rentals` | réserver | 201, 404, 409, 422 |
| `GET` | `/api/rentals/{id}` | consulter une location | 200, 404 |
| `POST` | `/api/rentals/{id}/confirm` | confirmer | 200, 409, 422 (échue) |
| `POST` | `/api/rentals/{id}/cancel` | annuler | 200, 409 |
| `POST` | `/api/rentals/{id}/pickup` | départ du véhicule | 200, 409, 422 |
| `POST` | `/api/rentals/{id}/return` | retour du véhicule | 200, 409, 422 |
| `POST` | `/api/rentals/{id}/close` | clôture | 200, 409 |

Les choix :

- **Les actions sont des `POST` sur des sous-ressources**
  (`/confirm`, `/cancel`…), jamais un `PATCH` du statut. Le client exprime une
  intention métier, et ne peut pas écrire un statut arbitraire : c'est
  Tell-Don't-Ask appliqué à l'API.
- **Aucun agrégat dans le JSON** : les contrôleurs renvoient des DTO
  (`infrastructure/in/rest/dto`) construits depuis les vues de l'application.
  Un test vérifie que ni événements, ni version, ni identifiants enveloppés ne
  fuient.
- **La validation Jakarta ne vérifie que la forme** (champs présents, bornes
  simples) : 400. Les règles métier restent dans le domaine, seul juge : 422.
- **Aucun `try/catch` dans les contrôleurs** : toutes les erreurs passent par
  `GlobalExceptionHandler`, au format RFC 9457 (`ProblemDetail`).
- **Ni pagination, ni filtre, ni tri, ni authentification** : coupes assumées
  (voir `BACKLOG.md`). L'absence d'authentification est la plus importante :
  à traiter avant tout déploiement public.

---

## 10. Tarification

La tarification compose deux politiques, **dans un ordre fixé par le domaine** :

1. `BaseDailyRatePolicy` : tarif journalier × jours facturables (25 h comptent
   pour 2 jours) ;
2. `LongDurationDiscountPolicy` : -10 % dès 7 jours, -20 % dès 30 jours. Le
   seuil porte sur la **durée réelle** : 6 j 23 h se facturent 7 jours, mais
   sans remise.

**L'ordre compte.** Une remise de 20 % suivie d'une majoration de 15 € ne donne
pas le même total que l'inverse (2 415 € contre 2 412 € sur 3 000 €). Si les
politiques étaient des beans injectés en `List`, leur ordre dépendrait du
conteneur : ce serait un bug non déterministe. Elles sont donc assemblées en
clair dans `PricingService.standard()`, et un test fige cet ordre.

**Arrondi à chaque ligne.** Chaque politique produit une ligne de facture
arrondie au centime, et le total est la somme des lignes. Le client peut
vérifier chaque montant affiché. L'écart avec un arrondi unique en fin de
calcul (un centime au plus par ligne) est assumé et testé.

**Le prix est figé à la réservation** (`RentalQuote` : tarif journalier et
total). Une hausse de tarif ultérieure ne change ni le prix ni la pénalité d'un
contrat signé. La **pénalité de retard** n'est pas une politique de prix mais
une règle de retour, portée par `Rental` : 150 % du tarif figé par jour de
retard entamé. **Un retour anticipé ne donne lieu à aucun remboursement**,
c'est une décision métier consignée dans `Rental.returnVehicle`.

---

## 11. Arborescence

```
autohub/
├── pom.xml                          # parent, gestion des versions
├── docker-compose.yml               # Postgres + Prometheus + Grafana
├── README.md
├── BACKLOG.md                       # idées mises de côté (discipline de périmètre)
├── docs/
│   ├── adr/                         # décisions d'architecture (0001 à 0005)
│   ├── sprint-we4-we5.md            # notes : Rental, GiST, mapping, revues
│   └── sprint-we6-we7.md            # notes : cas d'usage, API, tarification, événements
├── ops/prometheus.yml
├── .github/workflows/ci.yml
│
├── autohub-domain/                  # ⭐ zéro dépendance
│   └── src/main/java/com/ocarius/autohub/
│       ├── shared/domain/           # noyau partagé, volontairement minuscule
│       │   ├── AggregateRoot, DomainEvent, DomainException, EntityId
│       │   ├── BusinessRuleViolation, ConflictException, NotFoundException
│       │   ├── VehicleId            # seul identifiant partagé entre contextes
│       │   └── vo/  Money, DateRange
│       ├── fleet/domain/
│       │   ├── model/      Vehicle, Vin, PlateNumber, Mileage, VehicleModel,
│       │   │               VehicleStatus, VehicleCondition, Offering
│       │   ├── event/      VehicleAddedToFleet, VehicleMileageUpdated,
│       │   │               VehicleOfferingChanged, VehicleSold, VehicleWithdrawn
│       │   └── exception/  IllegalVehicleTransition, VehicleNotFound,
│       │                   VehicleAlreadyRegistered, ConcurrentVehicleModification
│       └── rental/domain/
│           ├── model/      Rental, RentalId, CustomerId, RentalPeriod, RentalQuote,
│           │               RentalStatus, VehicleHandover
│           ├── pricing/    PricingService, PricingPolicy, BaseDailyRatePolicy,
│           │               LongDurationDiscountPolicy, PriceLine, PriceBreakdown
│           ├── event/      RentalReserved, RentalConfirmed, RentalCancelled, RentalExpired,
│           │               VehiclePickedUp, VehicleReturned, LateReturnPenaltyApplied
│           └── exception/  IllegalRentalTransition, VehicleNotAvailable, VehicleNotRentable,
│                           RentalNotFound, UnknownVehicle, ConcurrentRentalModification
│
├── autohub-application/             # sans Spring
│   ├── .../shared/application/port/out/  EventPublisher, UnitOfWork
│   ├── .../fleet/application/   port/in (5 cas d'usage), port/out/VehicleRepository, service/
│   └── .../rental/application/  port/in (9 cas d'usage), port/out/RentalRepository,
│                                VehicleCatalog, service/
│
├── autohub-infrastructure/
│   ├── .../shared/infrastructure/
│   │   ├── error/GlobalExceptionHandler      # 422 / 409 / 404, RFC 9457
│   │   ├── event/SpringDomainEventPublisher  # bus Spring, livraison après commit
│   │   ├── transaction/SpringUnitOfWork
│   │   └── config/ClockConfiguration         # horloge de l'agence
│   ├── .../fleet/infrastructure/
│   │   ├── in/rest/        VehicleController + dto
│   │   ├── in/event/       VehicleReturnedListener   # écoute rental (ADR 0004)
│   │   ├── out/persistence/ VehicleJpaEntity, VehicleMapper, VehicleRepositoryAdapter
│   │   └── config/         FleetUseCaseConfiguration
│   ├── .../rental/infrastructure/
│   │   ├── in/rest/        RentalController, AvailabilityController + dto
│   │   ├── in/scheduling/  ReservationExpiryScheduler
│   │   ├── out/persistence/ RentalJpaEntity, RentalMapper, RentalRepositoryAdapter
│   │   ├── out/catalog/    JdbcVehicleCatalog    # couche anti-corruption vers vehicle
│   │   └── config/         RentalUseCaseConfiguration
│   └── src/main/resources/db/migration/
│       ├── V1__baseline.sql         # extensions btree_gist, pgcrypto
│       ├── V2__fleet_and_rental.sql # tables vehicle, rental + rental_no_overlap
│       └── V3__vehicle_lifecycle_and_rental_pricing.sql
│
└── autohub-bootstrap/
    ├── .../AutoHubApplication.java
    └── src/test/java/.../
        ├── architecture/HexagonalArchitectureTest.java
        ├── testing/        AbstractPostgresIntegrationTest, MutableClock
        ├── rental/infrastructure/in/rest/RentalHttpJourneyTest
        └── rental/infrastructure/out/persistence/
            RentalRepositoryAdapterTest, RentalConcurrencyTest
```

### Structure d'un contexte métier (en place pour `rental` et `fleet`)

```
com.ocarius.autohub.rental/
├── domain/
│   ├── model/          Rental, RentalPeriod, RentalStatus…
│   ├── pricing/        PricingService et politiques
│   ├── event/          RentalReserved, VehicleReturned…
│   └── exception/      VehicleNotAvailableException…
├── application/
│   ├── port/in/        ReserveRentalUseCase + commandes et vues
│   ├── port/out/       RentalRepository, VehicleCatalog
│   └── service/        ReserveRentalService… (un par cas d'usage)
└── infrastructure/
    ├── in/rest/        RentalController + dto
    ├── in/scheduling/  ReservationExpiryScheduler
    ├── out/persistence/ RentalJpaEntity, RentalMapper, RentalRepositoryAdapter
    └── config/         assemblage des cas d'usage
```

Point à ne pas rater : `RentalJpaEntity` et `Rental` sont **deux classes
distinctes**, reliées par un mapper. C'est ce qui coûte le plus d'efforts au
début, et ce qui rapporte le plus ensuite — le domaine n'a jamais à se plier
aux exigences de l'ORM.

---

## 12. Stratégie de test

| Type | Portée | Outils | Vitesse | Volume actuel |
|---|---|---|---|---|
| Unitaire domaine | un agrégat, un VO, la tarification | JUnit + AssertJ | 0,74 s au total | 173 tests |
| Cas d'usage | orchestration, transactions, événements | JUnit + fakes, **sans Spring ni Mockito** | ms | 20 tests |
| Architecture | tout le projet | ArchUnit | s | 12 règles |
| Intégration | persistance + Postgres réel | Testcontainers | s | adaptateurs, contrainte, fuseau |
| Concurrence | contrainte d'exclusion | Testcontainers + `CountDownLatch` | s | 1 test, le plus important |
| Bout en bout | API HTTP complète | MockMvc + Testcontainers | s | parcours, erreurs, expiration |

Convention de nommage : les tests décrivent une **règle**, pas une mécanique.
`additionner_deux_devises_differentes_est_refuse` se lit comme une
spécification ; `testPlus2` ne dit rien à personne.

Dans les tests du domaine, un astérisque en tête de `@DisplayName` signale un
cas limite souvent oublié : retour pile à l'heure, confirmation à l'instant
exact de l'échéance, expiration idempotente, etc.

Le temps est toujours piloté par un `Clock` fixe ou pilotable (`MutableClock`),
jamais par `Thread.sleep` ni par l'horloge système. Les tests d'intégration
remplacent le bean `Clock` de l'application : « 31 minutes plus tard » s'écrit
`clock.advance(Duration.ofMinutes(31))`. Les tests d'intégration ne sont **pas** `@Transactional`,
parce que la contrainte d'exclusion et le verrouillage optimiste ne se
manifestent qu'entre transactions distinctes. Les tests de `autohub-bootstrap`
tournent avec une JVM en `Europe/Paris`, pour que les bugs de fuseau soient
visibles aussi en CI.

---

## 13. Feuille de route

| Phase | Week-ends | Contenu | État |
|---|---|---|---|
| 0 | 1-2 | Fondations, noyau de domaine, CI, déploiement | ✅ |
| 1 | 3-5 | Agrégats `Vehicle` et `Rental`, contrainte GiST | ✅ |
| 2 | 6 | Cas d'usage, API REST, réservation avec TTL | ✅ |
| 3 | 7 | Tarification, retour véhicule, pénalités, kilométrage par événement | ✅ (compressée de 2 week-ends à 1) |
| 4 | 8-9 | Greffe de la vente (avancée par la compression des phases 2-3) | ⬜ |
| 5 | 12-13 | Observabilité, outbox | ⬜ |
| — | 14-15 | Documentation, marge | ⬜ |

Détail complet dans [`docs/roadmap-autohub-projet-1.md`](docs/roadmap-autohub-projet-1.md).

### Reste à faire

Phases 1 à 3, bilans complets dans [`docs/sprint-we4-we5.md`](docs/sprint-we4-we5.md)
et [`docs/sprint-we6-we7.md`](docs/sprint-we6-we7.md) :

- [x] Écrire l'agrégat `Vehicle` (week-end 3, sauté) et compléter la table
      `vehicle` (V3).
- [x] Traduire `OptimisticLockingFailureException` en exception du domaine,
      sinon elle deviendra un 500 au lieu d'un 409.
- [x] Répondre 409, et non 422, aux conflits : `ConflictException` (README §3.5).
- [x] Déclarer un bean `Clock` dans le fuseau de l'agence (`Europe/Paris`), pas
      en UTC : `LocalDateTime.now(clock)` en dépend.
      *Fait : `ClockConfiguration`, fuseau réglable par `autohub.agency.time-zone`.*
- [x] Passer l'ADR 0002 de « proposé » à « accepté » : il est implémenté et testé.
- [x] Traitement d'expiration, et libération immédiate d'un créneau échu à la
      réservation suivante.
- [ ] **Authentification** : l'API est ouverte, `customerId` est déclaratif.
- [ ] Outbox (phase 5) : un événement peut se perdre entre le commit et sa
      livraison.
- [ ] Revue WE7, points 4, 5 et 7 (`docs/sprint-we6-we7.md`) : règle « louable »
      dupliquée en SQL, événement publié hors transaction ignoré sans bruit,
      course rare entre le job d'expiration et une réservation.
- [ ] Avant `sales` (WE8) : écrire les règles ArchUnit `sales` ↔ `fleet`.

---

## 14. Déploiement

- [x] `mvn verify` passe au vert en local
- [x] `docker compose up -d` démarre PostgreSQL
- [ ] La CI GitHub Actions est verte sur `main`
- [ ] **L'application est déployée** quelque part, même vide

> Le dernier point n'est pas négociable. Un projet qui n'est jamais déployé ne
> se déploie jamais : la dette de déploiement grossit silencieusement jusqu'à
> devenir un chantier à part entière, généralement découvert en décembre.
