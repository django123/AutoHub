# AutoHub — Vente & Location de véhicules
## Roadmap technique détaillée · Septembre → Décembre 2026

---

## 1. Cadrage

### Objectif du projet
Construire un système de **location et vente de véhicules** en Java/Spring Boot, avec une architecture hexagonale rigoureuse, servant de support de révision POO et de pièce de portfolio.

### Objectifs pédagogiques (par ordre de priorité)
1. **POO / DDD tactique** — agrégats, value objects, invariants protégés, Tell-Don't-Ask, Loi de Déméter
2. **Architecture hexagonale** — ports/adapters, inversion de dépendance, domaine sans annotation framework
3. **PostgreSQL avancé** — plages temporelles, contraintes d'exclusion, verrouillage optimiste
4. **Tests** — unitaires sur le domaine (sans Spring), intégration avec Testcontainers
5. **Observabilité** — métriques métier, traces, logs structurés

### Ce qui est HORS périmètre (à assumer explicitement)
- Frontend riche → API REST + un client HTTP (Bruno/Postman) suffisent
- Paiement réel → un adapter `FakePaymentGateway` en phase 1
- Kafka / Kafka Connect → **phase 3, en janvier**, quand le projet 2 existera
- Authentification fine → Spring Security basique, Keycloak plus tard
- Gestion des sinistres, assurance, entretien → hors scope

> **Règle d'or** : chaque fois que tu es tenté d'ajouter une fonctionnalité, note-la dans un fichier `BACKLOG.md` et continue. Le périmètre est l'ennemi n°1.

### Budget réel
- **17 week-ends** entre le 1er septembre et le 31 décembre
- Hypothèse : **1 jour réellement travaillé par week-end**, soit ~110 heures utiles
- Marge de sécurité : décembre est amputé (fêtes) → **le projet doit être livrable au 15 décembre**

---

## 2. Stack technique

| Couche | Choix | Justification |
|---|---|---|
| Langage | Java 21 LTS (ou 25 LTS) | Records, sealed interfaces, pattern matching — utiles pour les value objects |
| Framework | Spring Boot 3.5.x | *Vérifier la version courante au démarrage* |
| Persistance | PostgreSQL 16+ / JPA-Hibernate | `tstzrange`, `EXCLUDE USING gist`, `JSONB` |
| Migrations | Flyway | Versionné, lisible, pas de `ddl-auto` |
| Tests | JUnit 5, AssertJ, Testcontainers, ArchUnit | ArchUnit pour **verrouiller l'hexagone** |
| Build | Maven ou Gradle | Multi-modules recommandé (voir §4.2) |
| Conteneurs | Docker Compose | Postgres + app + Grafana stack |
| Observabilité | Micrometer + Prometheus + Grafana + OpenTelemetry | Réutilise ton expérience existante |

---

## 3. Modèle de domaine

### 3.1 Découpage en contextes

Trois modules métier + un noyau partagé. **Modular monolith** : un seul déployable, mais des frontières de packages strictes, vérifiées par ArchUnit.

```
        ┌──────────────────────────────────────┐
        │            SHARED KERNEL             │
        │  Money · DateRange · DomainEvent     │
        └──────────────────────────────────────┘
                          ▲
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   ┌────┴─────┐     ┌─────┴─────┐     ┌─────┴─────┐
   │  FLEET   │◄────│  RENTAL   │     │   SALES   │
   │ (parc)   │     │ (location)│     │  (vente)  │
   └──────────┘     └───────────┘     └───────────┘
        ▲                 │                 │
        └─────────────────┴─────────────────┘
              via événements de domaine
```

**Principe clé** : `RENTAL` et `SALES` ne manipulent **jamais** l'agrégat `Vehicle` directement. Ils référencent un `VehicleId` et communiquent via événements. C'est ce qui rendra la greffe de la vente (phase 4) indolore.

---

### 3.2 Contexte FLEET — le parc

#### Agrégat racine : `Vehicle`

| Attribut | Type | Note |
|---|---|---|
| `id` | `VehicleId` (UUID) | |
| `vin` | `Vin` (VO) | 17 caractères, validé à la construction |
| `plate` | `PlateNumber` (VO) | Format FR : `AA-123-BB` |
| `model` | `VehicleModel` (VO) | marque, modèle, année, motorisation |
| `mileage` | `Mileage` (VO) | ne peut que croître — invariant |
| `condition` | `VehicleCondition` | `NEW`, `USED`, `DAMAGED` |
| `status` | `VehicleStatus` | machine à états, voir ci-dessous |
| `offerings` | `Set<Offering>` | `FOR_RENT`, `FOR_SALE`, ou les deux |
| `version` | `long` | verrouillage optimiste JPA |

#### Machine à états `VehicleStatus`

```
ACQUIRED ──► IN_PREPARATION ──► IN_FLEET ──┬──► IN_MAINTENANCE ──┐
                                            │                     │
                                            │◄────────────────────┘
                                            │
                                            ├──► SOLD      (terminal)
                                            └──► RETIRED   (terminal)
```

**Transitions interdites à encoder explicitement** :
- On ne peut pas vendre un véhicule `IN_MAINTENANCE`
- On ne peut pas passer `IN_FLEET` sans avoir traversé `IN_PREPARATION`
- Aucune transition depuis un état terminal

#### Invariants à protéger dans l'agrégat
1. Le kilométrage ne décroît jamais
2. Un véhicule `SOLD` ou `RETIRED` ne peut plus recevoir d'offering
3. Retirer l'offering `FOR_RENT` est refusé s'il existe des locations futures → **cet invariant est inter-contextes**, donc traité en application service, pas dans l'agrégat

#### Événements publiés
`VehicleAddedToFleet` · `VehicleWithdrawn` · `VehicleMileageUpdated` · `VehicleOfferingChanged` · `VehicleSold`

---

### 3.3 Contexte RENTAL — le cœur du projet

#### Agrégat racine : `Rental`

| Attribut | Type | Note |
|---|---|---|
| `id` | `RentalId` | |
| `vehicleId` | `VehicleId` | référence, pas l'objet |
| `customerId` | `CustomerId` | |
| `period` | `RentalPeriod` (VO) | intervalle `[début, fin)` |
| `status` | `RentalStatus` | voir machine à états |
| `quote` | `RentalQuote` (VO) | prix figé à la réservation |
| `pickup` / `dropoff` | `VehicleHandover` (VO) | date réelle, kilométrage, carburant |
| `reservedUntil` | `Instant` | TTL de la réservation |

#### Machine à états `RentalStatus`

```
   DRAFT
     │ reserve()
     ▼
  RESERVED ──── expire() ────► EXPIRED   (terminal)
     │ confirm()      │
     ▼                └──── cancel() ──► CANCELLED (terminal)
  CONFIRMED
     │ pickUp()
     ▼
  PICKED_UP
     │ returnVehicle()
     ▼
  RETURNED
     │ close()
     ▼
   CLOSED   (terminal)
```

**Règles métier associées** :
- `RESERVED` expire après 30 minutes si non confirmée
- `cancel()` est refusé après `PICKED_UP`
- `returnVehicle()` exige un kilométrage ≥ celui du `pickUp`
- Un retour tardif génère des pénalités (calculées par `PricingService`)

#### Value object central : `RentalPeriod`

```java
public record RentalPeriod(LocalDateTime start, LocalDateTime end) {
    public RentalPeriod {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!end.isAfter(start)) {
            throw new InvalidRentalPeriodException(start, end);
        }
    }

    public boolean overlaps(RentalPeriod other) { ... }
    public Duration duration() { ... }
    public long billableDays() { ... }   // arrondi supérieur, règle métier
}
```

> C'est le VO à soigner : il concentre une vraie logique, se teste sans aucune infrastructure, et illustre parfaitement Tell-Don't-Ask.

#### L'invariant le plus important : pas de double réservation

Ne **jamais** l'implémenter avec un `SELECT ... WHERE overlaps` suivi d'un `INSERT`. C'est une race condition garantie sous charge.

**Solution : contrainte d'exclusion PostgreSQL.**

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE rental (
    id            uuid PRIMARY KEY,
    vehicle_id    uuid        NOT NULL,
    customer_id   uuid        NOT NULL,
    period        tstzrange   NOT NULL,
    status        varchar(20) NOT NULL,
    reserved_until timestamptz,
    version       bigint      NOT NULL DEFAULT 0,

    CONSTRAINT rental_no_overlap EXCLUDE USING gist (
        vehicle_id WITH =,
        period     WITH &&
    ) WHERE (status IN ('RESERVED', 'CONFIRMED', 'PICKED_UP'))
);
```

La base **refuse physiquement** le chevauchement. Ton adapter de persistance intercepte la violation et la traduit en `VehicleNotAvailableException` du domaine — le domaine ignore que PostgreSQL existe.

> **Exercice associé** : écrire un test d'intégration qui lance 50 threads réservant le même véhicule sur la même période, et vérifier qu'exactement 1 réussit. C'est le test le plus instructif du projet.

#### Événements publiés
`RentalReserved` · `RentalConfirmed` · `RentalCancelled` · `RentalExpired` · `VehiclePickedUp` · `VehicleReturned` · `LateReturnPenaltyApplied`

---

### 3.4 Contexte PRICING

Pas d'agrégat — un **service de domaine pur**, sans état, entièrement testable.

```java
public interface PricingPolicy {
    boolean appliesTo(RentalPeriod period, VehicleModel model);
    Money apply(Money base, RentalPeriod period);
}
```

Implémentations à écrire :
- `BaseDailyRatePolicy` — tarif journalier du modèle
- `LongDurationDiscountPolicy` — dégressif : −10 % dès 7 jours, −20 % dès 30
- `WeekendSurchargePolicy` — majoration samedi/dimanche
- `SeasonalPolicy` — haute saison (JSONB en base)
- `LateReturnPenaltyPolicy` — pénalité au prorata

Composition via une chaîne ordonnée. **Money en `BigDecimal` avec devise, jamais de `double`.**

---

### 3.5 Contexte SALES — phase 4

#### Agrégat racine : `SaleOrder`

```
DRAFT ──► OPTIONED ──► SIGNED ──► PAID ──► DELIVERED ──► CLOSED
             │            │
             └── expire() └── cancel() ──► CANCELLED
```

**Le vrai exercice d'architecture** : quand une `SaleOrder` passe à `SIGNED`, le véhicule doit sortir du parc locatif. Que faire des locations futures déjà réservées ?

Trois stratégies possibles — à trancher **et à documenter** :
1. Refuser la vente s'il existe des locations futures confirmées (le plus simple, le plus honnête)
2. Autoriser, annuler les locations, notifier les clients (le plus réaliste)
3. Autoriser, mais différer la livraison après la dernière location

Le fait que ce choix soit *possible sans réécrire le domaine* est la preuve que ton architecture tient. C'est le moment de vérité du projet.

---

## 4. Arborescence

### 4.1 Structure des packages

```
com.ocarius.autohub
│
├── shared/
│   ├── domain/
│   │   ├── AggregateRoot.java
│   │   ├── DomainEvent.java
│   │   ├── EntityId.java
│   │   └── vo/
│   │       ├── Money.java
│   │       ├── Currency.java
│   │       └── DateRange.java
│   └── infrastructure/
│       ├── outbox/           ← table outbox + publisher (phase 5)
│       ├── error/            ← @ControllerAdvice, ProblemDetail RFC 9457
│       └── config/
│
├── fleet/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Vehicle.java              ← agrégat racine
│   │   │   ├── VehicleId.java
│   │   │   ├── VehicleStatus.java
│   │   │   ├── Offering.java
│   │   │   └── vo/  (Vin, PlateNumber, Mileage, VehicleModel)
│   │   ├── event/
│   │   └── exception/
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/   ← AddVehicleToFleetUseCase, UpdateMileageUseCase…
│   │   │   └── out/  ← VehicleRepository, EventPublisher
│   │   └── service/  ← implémentations des use cases
│   └── infrastructure/
│       ├── in/rest/
│       │   ├── VehicleController.java
│       │   └── dto/          ← DTO ≠ domaine, jamais d'agrégat exposé
│       └── out/persistence/
│           ├── VehicleJpaEntity.java
│           ├── VehicleMapper.java
│           └── VehicleRepositoryAdapter.java
│
├── rental/          ← même structure
│   ├── domain/model/  (Rental, RentalPeriod, RentalStatus, VehicleHandover…)
│   ├── application/
│   └── infrastructure/
│
├── pricing/
│   └── domain/  (PricingPolicy + implémentations, PricingService)
│
├── sales/           ← phase 4
│
└── AutoHubApplication.java
```

### 4.2 Règles architecturales — à verrouiller par ArchUnit

À écrire **dès le week-end 1**, avant qu'il n'y ait quoi que ce soit à protéger :

```java
@ArchTest
static final ArchRule domain_ne_depend_pas_de_spring =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

@ArchTest
static final ArchRule domain_ne_depend_pas_de_infrastructure =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

@ArchTest
static final ArchRule contextes_isoles =
    noClasses().that().resideInAPackage("..rental..")
        .should().dependOnClassesThat().resideInAPackage("..sales..");

@ArchTest
static final ArchRule pas_de_jpa_hors_persistence =
    classes().that().areAnnotatedWith(Entity.class)
        .should().resideInAPackage("..infrastructure.out.persistence..");
```

> Ces quatre règles valent plus que toute discipline personnelle. Elles transforment « je fais attention » en « la CI échoue ».

### 4.3 Découpage Maven (optionnel, recommandé)

Si tu veux la contrainte physique plutôt que déclarative :

```
autohub-parent (pom)
├── autohub-shared
├── autohub-domain        ← ZÉRO dépendance externe (hors slf4j)
├── autohub-application   ← dépend de domain uniquement
├── autohub-infrastructure← dépend de application + Spring
└── autohub-bootstrap     ← @SpringBootApplication, assemble tout
```

Le module `domain` avec un `pom.xml` quasi vide est l'illustration la plus parlante de l'architecture hexagonale en entretien.

---

## 5. Roadmap week-end par week-end

### Phase 0 — Fondations (WE 1-2 · 6-14 sept.)

**WE 1**
- Init projet, Docker Compose (Postgres + pgAdmin), Flyway, Actuator
- Squelette de packages, les 4 règles ArchUnit, CI GitHub Actions
- **Déploiement d'une coquille vide** (Railway, Fly.io ou VPS) — non négociable

**WE 2**
- Value objects du kernel : `Money`, `DateRange`, avec tests exhaustifs
- `AggregateRoot`, `DomainEvent`, gestion d'erreurs `ProblemDetail`
- Testcontainers opérationnel

✅ **DoD** : `docker compose up` fonctionne, CI verte, app déployée, `/actuator/health` répond en ligne.

---

### Phase 1 — Domaine location (WE 3-5 · 20 sept. – 4 oct.)

**WE 3** — Agrégat `Vehicle` complet : VO, machine à états, invariants, tests unitaires **sans Spring**
**WE 4** — Agrégat `Rental` : `RentalPeriod`, machine à états, transitions interdites testées
**WE 5** — Persistance : entités JPA séparées, mappers, migration Flyway avec `EXCLUDE USING gist`, **test de concurrence 50 threads**

✅ **DoD** : le double-booking est physiquement impossible, prouvé par un test.

---

### Phase 2 — API & réservation (WE 6-7 · 11-18 oct.)

**WE 6** — Use cases entrants, `VehicleController`, `RentalController`, DTO, validation
**WE 7** — Réservation avec TTL, job d'expiration (`@Scheduled`), verrouillage optimiste, recherche de disponibilité

✅ **DoD** : parcours complet réserver → confirmer jouable de bout en bout via HTTP.

---

### Phase 3 — Tarification & retour (WE 8-9 · 25 oct. – 1er nov.)

**WE 8** — `PricingPolicy` + les 5 implémentations, composition, tests paramétrés
**WE 9** — `pickUp()` / `returnVehicle()`, pénalités de retard, mise à jour du kilométrage via événement `VehicleReturned` → contexte `FLEET`

✅ **DoD** : cycle de vie complet d'une location, du devis à la clôture.

---

### Phase 4 — Greffe de la vente (WE 10-11 · 8-15 nov.)

**WE 10** — Agrégat `SaleOrder`, machine à états, API
**WE 11** — Résolution du conflit vente/location : implémenter la stratégie choisie, **documenter la décision dans un ADR**

✅ **DoD** : la vente est ajoutée **sans aucune modification** du domaine `rental`. Si tu as dû le toucher, c'est un enseignement en soi — écris pourquoi.

---

### Phase 5 — Observabilité & finition (WE 12-13 · 22-29 nov.)

**WE 12** — Micrometer, métriques métier (`rentals.reserved`, `rentals.expired`, taux d'occupation), dashboard Grafana, traces OpenTelemetry, logs structurés JSON avec `correlationId`
**WE 13** — Table outbox transactionnelle (sans Kafka, publication interne — Kafka viendra s'y brancher en janvier)

✅ **DoD** : dashboard opérationnel, une requête traçable de bout en bout.

---

### Marge — (WE 14-15 · 6-13 déc.)

README sérieux, diagrammes C4, ADR, jeu de données de démo, collection Bruno, vidéo de démo 3 min.

> **Cette marge n'est pas optionnelle.** Elle absorbera les dérapages des phases précédentes. Si par miracle elle est libre, la documentation est ce qui transforme un projet personnel en pièce de portfolio.

---

## 6. Pièges identifiés

| Piège | Parade |
|---|---|
| Réutiliser les entités JPA comme objets de domaine | ArchUnit dès le WE 1 + mappers explicites |
| Anemic domain model (getters/setters partout) | Aucun setter public sur les agrégats. Que des méthodes métier : `confirm()`, `pickUp()` |
| Ajouter Kafka « parce que c'est mon stack » | Interdit avant janvier. L'outbox suffit |
| Le week-end perdu en configuration | Objectif écrit **avant** de commencer. Timeboxer à 2h toute galère de config |
| Refactorer indéfiniment le domaine | Une passe de refacto par phase, pas plus |
| Vouloir 100 % de couverture | Domaine : viser 90 %+. Infrastructure : les chemins critiques |
| Repousser le déploiement | Déployé dès le WE 1. Un projet jamais déployé ne se déploie jamais |

---

## 7. Ce qui vient après (janvier 2027)

- **Projet 2** — boutique électronique sur Rails/Solidus
- **Phase Kafka** — Debezium CDC sur les deux Postgres, connecteurs sink vers Elasticsearch
- **Service transverse** de facturation en Spring Boot, consommant les événements des deux domaines
- **Observabilité distribuée** — un `trace_id` traversant Rails → Kafka → Spring Boot

---

*Document de travail — à réviser à la fin de chaque phase.*
