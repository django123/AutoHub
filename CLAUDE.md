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

## Commandes
- Build complet : `mvn verify`
- Lancer l'app : `mvn install -DskipTests` **puis** `mvn -pl autohub-bootstrap spring-boot:run`
  (le `install` est obligatoire : `-pl` réduit le réacteur à un module, Maven doit
  alors résoudre `autohub-infrastructure` depuis `~/.m2`, où `verify` ne publie rien.
  `-am` ne convient pas : il exécuterait `spring-boot:run` sur chaque module, à
  commencer par le POM parent, qui n'a pas de classe `main`. À rejouer après chaque
  modification d'un module amont.)
- Base locale : `docker compose up -d` — publiée sur le port **15432**, pas 5432
- Tests domaine seuls : `mvn -pl autohub-domain test`

## Environnement de cette machine
- Maven doit tourner sur le JDK 21 (`JAVA_HOME`), pas sur le 17.
- Le port 5432 est déjà occupé par un service PostgreSQL Windows natif
  (`postgresql-x64-16`). Windows laisse deux serveurs écouter le même port, la
  connexion partant alors vers l'un ou l'autre au hasard : d'où le port 15432.
- Les ports 8080, 8081 et 8090 sont pris par d'autres processus.

## Ce que j'attends de toi
- Explique le POURQUOI avant le COMMENT
- Signale mes erreurs de conception, même si je ne demande rien
- Propose des alternatives quand un choix est discutable
- Ne génère pas de code métier sans que je te l'aie explicitement demandé
- Commentaires en français, identifiants en anglais
