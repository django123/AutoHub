# Backlog — idées mises de côté

> Règle du projet : toute idée qui surgit pendant un week-end atterrit ici
> **au lieu d'être implémentée**. Le périmètre est l'ennemi n°1 d'un projet
> personnel. On relit ce fichier à la fin de chaque phase, et on est libre
> de décider que rien n'en vaut la peine.

## Dette technique assumée (phase 0)

- `LocalDateTime` plutôt que `ZonedDateTime` dans `DateRange`. Acceptable pour
  un opérateur mono-fuseau, faux dès qu'on ouvre une agence hors métropole.
- ~~`LoggingEventPublisher` ne publie rien de réel.~~ Remplacé au WE6 par
  `SpringDomainEventPublisher` (bus Spring, livraison après commit). Reste à
  faire en phase 5 : l'outbox transactionnelle, pour ne plus perdre un événement
  si l'application s'arrête entre le commit et sa livraison (ADR 0004).
- **Aucune authentification, alors que l'API existe depuis le WE6.**
  `customerId` est fourni par le client dans le corps de la requête : n'importe
  qui peut réserver, annuler ou clore au nom de n'importe qui. Spring Security
  était prévu « avec l'API ». À traiter avant tout déploiement public.

## ~~À trancher au week-end 8~~ Tranché au WE7 — arrondi dans la chaîne de tarification

> **Décision : option 1, arrondi à chaque ligne.** Chaque politique produit une
> ligne de facture (`PriceLine`) arrondie au centime, et le total est la somme
> des lignes. Le client peut vérifier chaque montant affiché, et le total ne
> contient aucun centime invisible. L'écart avec un arrondi unique en fin de
> calcul est assumé et figé par
> `PricingServiceTest.Arrondi.arrondi_par_ligne_contre_arrondi_final`. Le texte
> ci-dessous est conservé pour l'historique du raisonnement.

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

## Coupé au WE6-WE7, à reprendre en janvier

- **Pagination, filtres et tri** sur `GET /api/vehicles/available`. Coupe
  assumée de la session WE6.
- **Trois politiques de tarification** : majoration week-end, saisonnalité
  (JSONB en base) et franchise de retard. Deux politiques suffisent à démontrer
  la composition ordonnée.
- **Documentation OpenAPI soignée** : WE12 s'il reste du temps.
- **Endpoints `fleet` manquants** : maintenance, vente, retrait et changement
  d'offre existent dans l'agrégat et sont testés, mais n'ont pas d'API. La
  vente arrivera avec `sales`.

## Né pendant le WE6-WE7

- **`Idempotency-Key` sur `POST /api/rentals`.** Un double-clic ou un retry
  réseau envoie deux réservations identiques. La seconde reçoit un 409
  (`VEHICLE_NOT_AVAILABLE`), techniquement juste mais trompeur : le client
  croit avoir échoué alors que sa réservation existe. Il faudrait stocker
  `(clé, empreinte de la requête, réponse)` dans une table à clé unique, avec
  une expiration de 24 h, et rejouer la réponse enregistrée. Raisonnement
  complet dans `docs/sprint-we6-we7.md`, bloc 6.4.
- **Franchise de retard** : aujourd'hui, une minute de retard coûte un jour de
  pénalité. Une tolérance de 30 à 59 minutes est l'usage dans la profession.
- **Grille tarifaire séparée du véhicule** : le tarif journalier est porté par
  `Vehicle`, par simplicité. Une grille par catégorie, propre au contexte
  `rental` ou `pricing`, découplerait les deux.
- **Retirer `FOR_RENT` d'un véhicule qui a des locations futures** : la roadmap
  §3.2 l'interdit, mais c'est un invariant inter-contextes que l'agrégat
  `Vehicle` ne peut pas vérifier. Il faudra un cas d'usage `fleet` qui
  interroge `rental` par un port, ou un refus a posteriori par événement.
- **Rejeu des échecs de mise à jour du kilométrage** : aujourd'hui, ils ne sont
  que journalisés en ERROR. L'outbox (phase 5) rendra le rejeu automatique.

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
