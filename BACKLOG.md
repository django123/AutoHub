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

## À trancher au week-end 8 — arrondi dans la chaîne de tarification

`Money` normalise l'échelle sur celle de la devise à chaque construction. C'est
indispensable à `equals` (voir README §4), mais cela signifie qu'une chaîne de
`PricingPolicy` **arrondit au centime à chaque maillon**.

Mesuré sur le code actuel : enchaîner deux remises de 10 % diverge d'un centime
de la remise unique de 19 % équivalente sur **21 % des montants** entre 100 et
2000 EUR, et dans les deux sens. Avec cinq politiques, on arrondit cinq fois.

Trois options, à départager avant d'écrire `PricingService` :

1. **Assumer l'arrondi par étape.** Défendable si chaque politique correspond à
   une ligne distincte sur la facture — le client voit et vérifie chaque montant.
2. **Accumuler les coefficients, arrondir une seule fois à la fin.** `PricingPolicy`
   ne retourne plus un `Money` mais un facteur ou un delta ; le `Money` final est
   construit en dernier. Signature actuelle `Money apply(Money, RentalPeriod)` à
   revoir.
3. **Travailler en centimes entiers** (`long`) dans la chaîne, `Money` seulement aux
   frontières. Exact, mais on perd la lisibilité du domaine.

Le test `MoneyTest.Operations.enchainer_des_remises_arrondit_a_chaque_etape` fige
le comportement actuel pour que la décision soit prise en connaissance de cause.

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
