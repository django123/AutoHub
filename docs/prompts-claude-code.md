# Prompts Claude Code — AutoHub

> **Principe directeur.** L'objectif du projet est de *réviser*, pas de
> *générer*. Un prompt qui produit 400 lignes de code correct t'apprend moins
> qu'un prompt qui te fait écrire 40 lignes et te dit pourquoi elles sont
> fausses.
>
> Les prompts ci-dessous sont donc de trois natures :
> - **🔨 Génération** — pour l'échafaudage sans valeur pédagogique
> - **🧠 Socratique** — Claude questionne, tu codes
> - **🔍 Revue** — tu codes, Claude critique
>
> Règle pratique : **tout ce qui touche au domaine, tu l'écris toi-même.**
> Claude génère l'infrastructure et relit le métier. Si tu inverses, tu auras
> un beau dépôt et aucune compétence de plus en décembre.

---

## Mise en place

### CLAUDE.md à placer à la racine du projet

Ce fichier est lu automatiquement à chaque session. Il évite de répéter le
contexte à chaque prompt.

```markdown
# AutoHub — contexte pour Claude Code

## Nature du projet
Projet d'apprentissage. L'objectif est que JE progresse, pas que le code soit
écrit vite. Privilégie les explications et les questions à la génération.

## Objectifs pédagogiques, par ordre de priorité
1. POO / DDD tactique — agrégats, value objects, invariants, Tell-Don't-Ask
2. Architecture hexagonale — ports/adapters, domaine sans framework
3. PostgreSQL avancé — tstzrange, EXCLUDE USING gist, verrouillage optimiste
4. Tests — unitaires sans Spring, intégration Testcontainers
5. Observabilité — métriques métier, traces, logs structurés

## Règles non négociables
- Le module autohub-domain n'a AUCUNE dépendance. Jamais d'import Spring,
  JPA, Jackson ou Lombok dedans.
- Entités JPA et objets de domaine sont des classes distinctes, reliées par
  un mapper explicite.
- Pas de setter public sur un agrégat. Uniquement des méthodes métier.
- BigDecimal pour l'argent, jamais double.
- Intervalles temporels semi-ouverts [start, end).
- Flyway pour le schéma. ddl-auto reste à "validate".
- Pas de Kafka avant janvier.

## Ce que j'attends de toi
- Explique le POURQUOI avant le COMMENT
- Signale mes erreurs de conception, même si je ne demande rien
- Propose des alternatives quand un choix est discutable
- Ne génère pas de code métier sans que je te l'aie explicitement demandé
- Commentaires en français, code (identifiants) en anglais
```

### Réglages utiles

```bash
# Mode plan : Claude propose une stratégie avant de toucher au code.
# À utiliser systématiquement en début de week-end.
claude --permission-mode plan

# Session avec contexte persistant
claude
> /init          # génère un CLAUDE.md initial, à retravailler ensuite
```

---

## Phase 0 — Vérification (à faire maintenant)

### 🔍 Revue du squelette

```
Relis l'ensemble du projet et réponds à ces questions précises :

1. Le module autohub-domain a-t-il vraiment zéro dépendance ? Vérifie le
   pom.xml ET les imports de chaque fichier source.
2. Les règles ArchUnit vont-elles effectivement échouer si j'ajoute une
   annotation @Entity dans le domaine ? Prouve-le en décrivant ce que ferait
   ArchUnit, ne te contente pas de l'affirmer.
3. Y a-t-il une incohérence entre la sémantique de DateRange.overlaps() et
   celle de l'opérateur && de PostgreSQL sur un tstzrange '[)' ?

Ne corrige rien pour l'instant. Liste ce qui ne va pas.
```

### 🔨 Correction des erreurs de compilation

```
Lance `mvn verify` et corrige uniquement les erreurs de compilation et de
dépendances. Ne modifie aucune logique métier, aucune signature de méthode,
aucun commentaire. Si une correction demande un choix de conception,
arrête-toi et demande-moi.
```

### 🧠 Auto-évaluation

```
Interroge-moi sur le code de la phase 0, comme le ferait un examinateur.
Cinq questions, une à la fois, en attendant ma réponse avant la suivante.
Concentre-toi sur : le piège du scale de BigDecimal, la sémantique
semi-ouverte, pourquoi pullDomainEvents() vide la liste, et pourquoi
l'agrégat ne publie pas ses événements lui-même.

Si ma réponse est approximative, ne me félicite pas — dis-moi ce qui manque.
```

---

## Week-end 3 — Agrégat `Vehicle`

### 🧠 Conception (à faire AVANT d'écrire du code)

```
Je vais modéliser l'agrégat Vehicle. Ne code rien.

Pose-moi les questions qui vont me forcer à trancher les vraies décisions :
frontière de l'agrégat, ce qui est value object et ce qui ne l'est pas,
invariants qui appartiennent à l'agrégat par opposition à ceux qui le
dépassent, transitions d'états interdites.

Une question à la fois. Si ma réponse crée une incohérence avec une réponse
précédente, signale-la immédiatement.
```

### 🔍 Revue après implémentation

```
Je viens d'écrire l'agrégat Vehicle. Fais-en une revue critique selon
cette grille, sans rien corriger :

1. Un invariant peut-il être contourné ? Donne un exemple de code appelant
   qui laisserait l'agrégat dans un état invalide.
2. Y a-t-il des getters exposant l'état interne au lieu d'un comportement ?
   (violation Tell-Don't-Ask)
3. Des chaînes d'appels à plus d'un point ? (Loi de Déméter)
4. Une collection interne est-elle exposée sans copie défensive ?
5. La machine à états autorise-t-elle une transition qui ne devrait pas
   exister ?

Pour chaque problème : cite la ligne, explique la conséquence concrète en
production, propose une correction. Mais ne l'applique pas.
```

### 🧠 Tests

```
Je veux écrire les tests de Vehicle moi-même. Donne-moi uniquement la LISTE
des cas à couvrir, sous forme de noms de méthodes en français, groupés par
@Nested. Aucun corps de méthode.

Inclus les cas limites auxquels je n'ai probablement pas pensé.
```

---

## Week-end 4 — Agrégat `Rental`

### 🧠 Machine à états

```
Je dois implémenter la machine à états de Rental :
DRAFT → RESERVED → CONFIRMED → PICKED_UP → RETURNED → CLOSED
avec CANCELLED et EXPIRED comme états terminaux.

Compare pour moi trois approches d'implémentation en Java 21 :
(a) un enum avec méthodes abstraites
(b) des sealed interfaces avec pattern matching
(c) une simple table de transitions autorisées

Pour chacune : lisibilité, testabilité, facilité de persistance, et coût
d'ajout d'un nouvel état. Recommande-en une et argumente. Ne code pas.
```

### 🔍 Piège classique

```
Relis ma classe Rental et cherche spécifiquement cette faute :
des règles métier qui ont fui de l'agrégat vers la couche application.

Pour chaque règle que tu trouves dans un service alors qu'elle devrait être
dans l'agrégat, explique-moi comment j'aurais pu le détecter moi-même.
```

---

## Week-end 5 — PostgreSQL et la contrainte d'exclusion

### 🧠 Comprendre avant d'écrire

```
Explique-moi EXCLUDE USING gist en partant de zéro, avec cette progression :

1. Pourquoi un index B-tree ne peut pas indexer une plage
2. Ce que GiST fait différemment
3. Pourquoi btree_gist est nécessaire pour combiner "vehicle_id WITH ="
   et "period WITH &&"
4. Ce qui se passe exactement, au niveau du moteur, quand deux transactions
   concurrentes tentent d'insérer des plages qui se chevauchent

Utilise des exemples SQL que je peux exécuter dans psql pour vérifier
chaque affirmation. Je veux comprendre, pas copier.
```

### 🔨 Migration (génération acceptable)

```
Écris la migration Flyway V2__rental_schema.sql pour les tables vehicle
et rental.

Contraintes :
- rental.period en tstzrange, sémantique '[)'
- contrainte d'exclusion empêchant le chevauchement pour un même véhicule,
  UNIQUEMENT sur les statuts RESERVED, CONFIRMED, PICKED_UP
- colonne version pour le verrouillage optimiste
- index sur les colonnes réellement interrogées, et justifie chacun

Commente chaque bloc SQL en expliquant le pourquoi.
```

### 🧠 LE test du projet

```
Je veux écrire le test de concurrence : 50 threads réservant le même
véhicule sur la même période, exactement 1 doit réussir.

Ne l'écris pas. Explique-moi :
1. Pourquoi un simple ExecutorService avec 50 submit() ne suffit pas à
   provoquer une vraie concurrence, et ce qu'il faut ajouter
2. Comment garantir que les 50 threads partent réellement en même temps
3. Quelle exception JDBC remonte exactement lors d'une violation de
   contrainte d'exclusion, et comment la distinguer d'une autre violation
4. Où traduire cette exception en exception de domaine — et pourquoi
   surtout pas dans le domaine lui-même
```

### 🔍 Mapper domaine ↔ JPA

```
Relis mon RentalMapper et vérifie ces points :

1. Le domaine peut-il fuir vers l'entité JPA, ou l'inverse ?
2. La reconstruction d'un agrégat depuis la base passe-t-elle par les
   constructeurs métier — qui revalident les invariants — ou contourne-t-elle
   la validation ?
3. Si elle la contourne : est-ce un bug ou un choix défendable ? Argumente
   les deux positions avant de conclure.
```

---

## Week-ends 6-7 — API REST

### 🔨 Échafaudage (génération acceptable)

```
Génère le contrôleur REST pour les cas d'usage de réservation, avec ses DTO.

Règles :
- les DTO sont dans infrastructure/in/rest/dto, jamais dans le domaine
- aucun agrégat exposé directement dans une réponse HTTP
- validation Jakarta sur les DTO d'entrée uniquement
- les erreurs métier remontent via GlobalExceptionHandler, pas de try/catch
  dans le contrôleur

Ajoute ensuite un test MockMvc par endpoint.
```

### 🧠 Conception d'API

```
Question de conception, ne code pas.

Une réservation expire après 30 minutes. Trois options pour l'implémenter :
(a) un job @Scheduled qui balaie périodiquement
(b) une expiration paresseuse, évaluée à la lecture
(c) pg_cron côté base

Compare selon : justesse sous concurrence, charge sur la base, testabilité,
comportement si l'application redémarre. Recommande, argumente, et dis-moi
ce que tu ferais différemment à 10 000 réservations par jour.
```

---

## Week-ends 10-11 — La greffe de la vente

### 🧠 Le moment de vérité

```
Je m'apprête à ajouter le contexte sales. C'est le test réel de mon
architecture.

Avant que je commence : analyse le code existant et prédis PRÉCISÉMENT quels
fichiers je vais devoir modifier hors du package sales. Pour chacun, dis si
cette modification est légitime ou si elle révèle un défaut de conception.

Ensuite seulement, je coderai, et on comparera ta prédiction à la réalité.
```

### 🔍 Après implémentation

```
J'ai ajouté le contexte sales. Compare ta prédiction précédente avec ce que
j'ai réellement dû modifier.

Pour chaque écart : qu'est-ce que cela révèle de mon découpage initial ?
Sois direct, c'est l'intérêt de l'exercice.
```

---

## Week-ends 12-13 — Observabilité

### 🧠 Métriques métier

```
Je veux instrumenter AutoHub avec Micrometer. Ne code pas.

Distingue-moi clairement les métriques TECHNIQUES (latence, erreurs HTTP,
pool de connexions) des métriques MÉTIER. Pour ces dernières, propose-moi
cinq métriques qu'un exploitant de flotte regarderait vraiment — pas cinq
compteurs génériques.

Pour chacune : type Micrometer (counter, gauge, timer), cardinalité des tags,
et le risque d'explosion de cardinalité si je m'y prends mal.
```

### 🔨 Outbox

```
Implémente le pattern outbox transactionnel :
- table outbox écrite dans la MÊME transaction que l'agrégat
- OutboxEventPublisher remplaçant LoggingEventPublisher
- un relais qui publie et marque comme traité

Explique en commentaire pourquoi la publication directe vers un broker depuis
un @TransactionalEventListener(AFTER_COMMIT) ne suffit pas, et quel scénario
de panne précis l'outbox couvre.
```

---

## Prompts transverses

### 🔍 Revue de fin de phase

```
Fin de phase. Fais une revue d'architecture complète :

1. Une règle métier a-t-elle fui du domaine vers l'application ou
   l'infrastructure ?
2. Un agrégat est-il devenu anémique (getters/setters, logique ailleurs) ?
3. Les règles ArchUnit passent-elles encore, et une nouvelle règle
   mériterait-elle d'être ajoutée ?
4. Quelle est la classe la plus complexe du projet, et pourquoi ?
5. Si je devais expliquer ce code en entretien, quelle partie me mettrait
   en difficulté ?

Sois critique. Je n'ai pas besoin d'être rassuré.
```

### 🧠 Avant chaque week-end

```
Objectif du week-end : [décrire].
Temps disponible : [X] heures.

Découpe en tâches de 45 minutes maximum, ordonnées pour que je puisse
m'arrêter n'importe quand avec quelque chose qui compile et qui passe
les tests.

Signale-moi les tâches où je vais probablement déborder, et ce que je peux
couper sans compromettre l'objectif.
```

### 🔍 Quand tu es bloqué

```
Je suis bloqué depuis 45 minutes sur [problème].

Ne me donne pas la solution. Pose-moi trois questions qui vont me permettre
de la trouver. Si après mes réponses je suis toujours à côté, donne-moi un
indice — toujours pas la solution.
```

---

## Anti-patterns à éviter

| Prompt | Problème |
|---|---|
| « Implémente tout le contexte rental » | Tu obtiens du code que tu ne comprends pas. Zéro apprentissage. |
| « Corrige toutes les erreurs » | Tu ne sauras pas ce qui était cassé ni pourquoi. |
| « C'est bon comme ça ? » | Question fermée, réponse complaisante. Demande une critique structurée. |
| « Ajoute Kafka » | Hors périmètre jusqu'en janvier. Le prompt le plus dangereux du projet. |
| « Refactore ce fichier » | Sans critère explicite, tu obtiens du remaniement cosmétique. |

**Test à s'appliquer avant chaque prompt** : *si Claude répond parfaitement,
qu'est-ce que j'aurai appris ?* Si la réponse est « rien », reformule.
