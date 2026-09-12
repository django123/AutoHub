# Backlog — idées mises de côté

> Règle du projet : toute idée qui surgit pendant un week-end atterrit ici
> **au lieu d'être implémentée**. Le périmètre est l'ennemi n°1 d'un projet
> personnel. On relit ce fichier à la fin de chaque phase, et on est libre
> de décider que rien n'en vaut la peine.

## Dette technique assumée (phase 0)

- `LocalDateTime` plutôt que `ZonedDateTime` dans `DateRange`. Acceptable pour
  un opérateur mono-fuseau, faux dès qu'on ouvre une agence hors métropole.
- `LoggingEventPublisher` ne publie rien de réel. Remplacé en phase 5 par
  l'outbox transactionnelle.
- Aucune authentification. Spring Security arrive avec l'API (phase 2).

## Idées à trancher plus tard

- Recherche géographique des véhicules (PostGIS)
- Gestion des entretiens et immobilisations
- Tarification multi-devises (le `Money` le supporte déjà)
- Module de facturation PDF
- Interface d'administration

## Rejeté explicitement

- Microservices — voir `docs/adr/0001`
- GraphQL — REST suffit pour ce périmètre
- Kafka avant janvier — pas d'événements réels à transporter
