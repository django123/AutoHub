# Sessions Claude Code — WE6 & WE7
## API REST · Tarification · Retour véhicule

> **Deux week-ends séparés**, pas un sprint. Le WE6 expose le domaine, le WE7
> l'enrichit. Si le WE6 déborde, le WE7 absorbe — jamais l'inverse.
>
> **Rappel de la compression.** L'API passe de 2 week-ends à 1, la tarification
> de 2 à 1. Ce qui est coupé, explicitement :
> - pagination, filtres, tri sur les endpoints de liste
> - 3 des 5 politiques de tarification
> - la documentation OpenAPI soignée
>
> Ces coupes sont réversibles en janvier. Ce qui ne l'est pas : arriver en
> décembre sans la greffe de la vente, qui est la seule preuve réelle de ton
> architecture.

---

# WE6 — Exposer le domaine (7-8 h)

## Bloc 6.0 — Reprise (15 min)

```
État des lieux en 10 lignes maximum : ce qui est terminé, ce qui est à
moitié fait, ce qui risque de me bloquer aujourd'hui.

Vérifie que `mvn verify` passe et que les règles ArchUnit sont vertes.
Corrige uniquement ce qui est cassé, rien d'autre.
```

---

## Bloc 6.1 — Le cas d'usage, pas le service (45 min) 🧠

C'est la décision qui détermine si ta couche application reste lisible ou
devient un dépotoir.

```
Je vais écrire la couche application du contexte rental. NE CODE RIEN.

Pose-moi UNE question à la fois.

1. Une classe par cas d'usage (ReserveRentalService, ConfirmRentalService…)
   ou un RentalApplicationService regroupant tout ? Fais-moi argumenter les
   deux, puis demande-moi ce qui se passe au bout de 15 cas d'usage.

2. Où placer @Transactional : sur le cas d'usage, sur l'adaptateur de
   repository, ou sur le contrôleur ? Et pourquoi surtout pas dans le domaine ?

3. Un cas d'usage doit-il retourner l'agrégat, un DTO, ou juste un
   identifiant ? Quelles sont les conséquences de chaque choix sur le
   couplage entre couches ?

4. Qui recueille les événements de domaine via pullDomainEvents(), et à quel
   moment exact par rapport au commit ? Démonte ma réponse si elle publie
   avant le commit.

Si une réponse contredit une précédente, signale-le immédiatement.
```

**À savoir défendre en sortant de ce bloc :** pourquoi `@Transactional` sur le
cas d'usage et pas sur le contrôleur, et pourquoi publier un événement avant le
commit est un bug et non un détail de timing.

---

## Bloc 6.2 — Le TTL de réservation (45 min) 🧠

```
Une réservation expire 30 minutes après sa création si elle n'est pas
confirmée. Trois implémentations possibles :

(a) un job @Scheduled qui balaie périodiquement les réservations expirées
(b) une expiration paresseuse, évaluée à chaque lecture de la réservation
(c) pg_cron côté base

NE CODE PAS. Compare selon :
- justesse sous concurrence (deux threads lisant une réservation au moment
  précis où elle expire)
- interaction avec la contrainte d'exclusion : une réservation expirée
  doit-elle libérer le créneau immédiatement, et que se passe-t-il si le job
  tourne 30 secondes en retard ?
- comportement si l'application redémarre
- testabilité

Recommande-en une. Dis-moi ensuite ce que tu ferais différemment à
10 000 réservations par jour, et à quel volume ton choix actuel casse.
```

> **Le piège à voir venir.** La contrainte d'exclusion du WE5 filtre sur
> `status IN ('RESERVED','CONFIRMED','PICKED_UP')`. Tant qu'une réservation
> expirée n'a pas changé de statut en base, elle **bloque toujours le
> créneau**. Un job qui tourne toutes les 5 minutes, c'est jusqu'à 5 minutes
> de créneau gelé pour rien. L'expiration paresseuse ne résout pas ça non
> plus — elle ne libère que ce qu'on relit.

---

## Bloc 6.3 — Tu écris les cas d'usage (2 h) ⌨️

```
Je vais écrire les cas d'usage moi-même.

Donne-moi UNIQUEMENT la liste des ports entrants à créer, avec pour chacun :
son nom, sa signature (paramètres et retour), et en une ligne l'invariant
qu'il doit faire respecter.

Aucun code, aucune implémentation.
```

Ordre d'écriture :

1. Ports entrants : `ReserveRentalUseCase`, `ConfirmRentalUseCase`,
   `CancelRentalUseCase`, `FindAvailableVehiclesUseCase`
2. Port sortant `RentalRepository` (s'il n'existe pas déjà)
3. Les services d'application, un par cas d'usage
4. Les tests de ces services **avec des fakes en mémoire, sans Spring**

> Le point 4 est le plus instructif : si tu as besoin de Mockito pour tester
> un cas d'usage, c'est que tes ports sont trop gros ou trop nombreux. Un fake
> `InMemoryRentalRepository` de 20 lignes doit suffire.

---

## Bloc 6.4 — L'adaptateur REST (2 h) 🔨 puis ⌨️

Génération acceptable sur l'échafaudage, pas sur les règles.

```
Génère les contrôleurs REST et leurs DTO pour les cas d'usage de rental.

Règles strictes :
- les DTO vivent dans infrastructure/in/rest/dto, jamais dans le domaine
- aucun agrégat exposé dans une réponse HTTP
- validation Jakarta sur les DTO d'ENTRÉE uniquement
- aucun try/catch dans les contrôleurs : les erreurs remontent via
  GlobalExceptionHandler
- un contrôleur ne connaît que des interfaces de ports entrants, jamais une
  implémentation

Endpoints minimaux, rien de plus :
  POST   /api/rentals                 réserver
  POST   /api/rentals/{id}/confirm    confirmer
  POST   /api/rentals/{id}/cancel     annuler
  GET    /api/rentals/{id}            consulter
  GET    /api/vehicles/available      chercher des disponibilités

Pas de pagination, pas de filtres, pas de tri. C'est assumé.
```

Puis le point que je veux que tu traites toi-même :

```
Question de conception, ne code pas.

Deux requêtes POST /api/rentals identiques partent à 200 ms d'intervalle
(double-clic, ou retry réseau du client). Que se passe-t-il ?

Explique-moi :
1. Ce que fait la contrainte d'exclusion dans ce cas précis
2. Pourquoi un 409 renvoyé au client est techniquement correct mais
   fonctionnellement mauvais ici
3. Comment un en-tête Idempotency-Key résoudrait le problème, et ce qu'il
   faudrait stocker en base pour l'implémenter
4. Si ça vaut le coup à mon échelle, ou si c'est à mettre dans BACKLOG.md

Je veux comprendre le mécanisme même si je décide de ne pas l'implémenter.
```

---

## Bloc 6.5 — Verrouillage optimiste de bout en bout (45 min) 🧠 puis ⌨️

```
Le champ version existe depuis le WE5 mais je ne l'ai jamais vu agir.

Explique-moi la chaîne complète : deux utilisateurs confirment la même
réservation simultanément.
1. Quelle exception Hibernate lève-t-il exactement, et à quel moment
   (au save, ou au flush) ?
2. En quoi Spring l'enveloppe-t-il ?
3. Où dois-je la traduire, et en quelle exception de domaine ?
4. Quel code HTTP renvoyer, et pourquoi pas 500 ?

Donne-moi ensuite le squelette d'un test d'intégration qui le prouve —
uniquement le squelette, j'écris le corps.
```

---

## Clôture WE6 (30 min)

```
Bilan en quatre points, sois critique :
1. Qu'est-ce qui est réellement terminé ?
2. Qu'est-ce qui est à moitié fait et va me piéger la semaine prochaine ?
3. Les règles ArchUnit passent-elles ? Faut-il en ajouter une maintenant que
   la couche REST existe — par exemple interdire à un contrôleur de dépendre
   directement du domaine ?
4. En entretien, quelle partie de ce code me mettrait en difficulté ?

Mets ensuite à jour le tableau de feuille de route du README.
```

```bash
mvn verify
git commit -am "feat(rental): cas d'usage, API REST, expiration des réservations"
git push
```

### Definition of Done — WE6
- [ ] Un parcours complet réserver → confirmer est jouable en HTTP
- [ ] Les cas d'usage sont testés avec des fakes, **sans Spring**
- [ ] Aucun agrégat n'apparaît dans une réponse JSON
- [ ] Une réservation expire et libère son créneau (prouvé par un test)
- [ ] Un conflit de version renvoie un code HTTP correct
- [ ] `mvn verify` et la CI sont verts

---

# WE7 — Tarification et retour véhicule (7-8 h)

## Bloc 7.1 — Composer des politiques (1 h) 🧠

Le piège de ce bloc n'est pas le pattern Strategy, que tu connais. C'est
l'ordre.

```
Je vais implémenter la tarification par composition de politiques.
NE CODE PAS.

1. Donne-moi l'interface PricingPolicy la plus simple qui permette de
   composer plusieurs règles. Discute : faut-il qu'une politique reçoive le
   montant courant, ou le montant de base plus la liste des ajustements
   déjà appliqués ?

2. Question centrale : l'ordre d'application est-il commutatif ?
   Prends un exemple chiffré avec une remise longue durée de 20 % et une
   majoration week-end de 15 €, et montre-moi les deux ordres possibles et
   les deux résultats.

3. Si l'ordre compte, comment le rendre EXPLICITE dans le code plutôt que
   dépendant de l'ordre d'injection des beans Spring ? Compare au moins deux
   approches.

4. Où arrondir : à chaque étape, ou une seule fois à la fin ? Montre-moi un
   cas chiffré où la différence est visible sur la facture.
```

> **Ce que tu dois en retirer.** Un ensemble de politiques injecté par Spring
> arrive dans un ordre que tu ne contrôles pas, et qui peut changer entre
> deux versions. Si ton calcul dépend de l'ordre — et ici il en dépend — tu as
> un bug non déterministe, le pire genre. L'ordre doit être une donnée du
> domaine, pas un effet de bord du conteneur.

---

## Bloc 7.2 — Tu écris la tarification (2 h) ⌨️

```
Donne-moi UNIQUEMENT la liste des tests de tarification : noms de méthodes
en français, groupés par @Nested. Aucun corps.

Inclus obligatoirement des cas sur :
- la non-commutativité de l'ordre d'application
- l'arrondi quand plusieurs politiques s'enchaînent
- une location d'exactement 7 jours (seuil de la remise)
- une location de 6 jours et 23 heures (juste sous le seuil)

Marque d'un astérisque les cas que la plupart des développeurs oublient.
```

Deux politiques seulement, c'est assumé :

1. `BaseDailyRatePolicy` — tarif journalier × `billableDays()`
2. `LongDurationDiscountPolicy` — −10 % dès 7 jours, −20 % dès 30

Les trois autres (week-end, saison, pénalité) vont dans `BACKLOG.md`. La
pénalité de retard revient au bloc 7.4, mais comme règle de retour, pas
comme politique de prix.

---

## Bloc 7.3 — Le cycle `pickUp` / `returnVehicle` (1 h 30) ⌨️

Tu écris, sans assistance. Règles à faire respecter par l'agrégat :

- `pickUp()` n'est possible que depuis `CONFIRMED`
- `returnVehicle()` exige un kilométrage ≥ celui du départ
- un retour après `period.end()` déclenche une pénalité
- un retour anticipé ne donne **pas** lieu à remboursement (décision métier à
  consigner)

---

## Bloc 7.4 — Le moment architectural du week-end (1 h 30) 🧠

Celui-ci compte plus que la tarification. Prends-le au sérieux.

```
Au retour du véhicule, le kilométrage du Vehicle (contexte fleet) doit être
mis à jour depuis le contexte rental.

NE CODE PAS. Analyse ces quatre options :

(a) le cas d'usage de rental appelle directement VehicleRepository
(b) le cas d'usage de rental appelle un cas d'usage de fleet
(c) rental publie VehicleReturned, un listener dans fleet réagit dans la
    MÊME transaction
(d) rental publie VehicleReturned, fleet réagit APRÈS le commit, en
    cohérence différée

Pour chacune :
- que dit ma règle ArchUnit "rental ne dépend pas de sales" appliquée par
  analogie à fleet ? Laquelle de ces options la violerait ?
- que se passe-t-il si la mise à jour du kilométrage échoue ? Le retour du
  véhicule doit-il être annulé ?
- laquelle survit au découpage en microservices, si j'allais jusque-là ?

Recommande-en une et argumente. Pose-moi la question du compromis
cohérence immédiate / découplage avant de conclure.
```

Puis :

```
Écris l'ADR 0004 sur ce choix. Section "Alternatives écartées" obligatoire,
et une section explicite sur ce qu'on accepte de perdre.
```

> **Le vrai enseignement.** Si tu choisis (a), ArchUnit ne dira rien
> aujourd'hui — tu n'as pas écrit de règle `rental` ↛ `fleet`. Mais tu viens
> de créer le couplage qui rendra la greffe de la vente douloureuse au WE8.
> Écris la règle ArchUnit **maintenant**, avant de coder, pour que la barrière
> existe avant la tentation. C'est exactement ce qu'on a fait au WE1.

---

## Bloc 7.5 — Revue critique (45 min) 🔍

```
Revue critique de la tarification et du cycle de retour. NE CORRIGE RIEN.

1. Le calcul de prix est-il déterministe quel que soit l'ordre d'injection
   des beans ? Prouve-le ou montre le contre-exemple.
2. Une politique peut-elle produire un montant négatif ? Écris le cas.
3. L'agrégat Rental dépend-il de l'horloge système plutôt que d'un Clock
   injecté ?
4. Des règles métier ont-elles atterri dans le service d'application plutôt
   que dans l'agrégat ou les politiques ?
5. Le listener de VehicleReturned peut-il échouer silencieusement ? Que
   voit-on dans les logs si c'est le cas ?
6. Si je rejoue deux fois le même événement VehicleReturned, que se
   passe-t-il ? (question d'idempotence)

Cite les lignes, décris la conséquence en production, propose la correction
sans l'appliquer.
```

---

## Clôture WE7 (30 min)

```
Bilan de fin de phase 3, en cinq points :
1. Qu'est-ce qui est terminé ?
2. Qu'est-ce qui est à moitié fait ?
3. Quelle est la classe la plus complexe du projet maintenant, et pourquoi ?
4. Avant d'ajouter le contexte sales au prochain week-end : quels couplages
   existants vont me gêner ? Sois précis, cite les fichiers.
5. En entretien, quelle partie me mettrait en difficulté ?

Mets à jour le README.
```

```bash
mvn verify
git commit -am "feat(pricing): politiques de tarification, retour véhicule, pénalités"
git push
```

### Definition of Done — WE7
- [ ] Le cycle complet devis → réservation → confirmation → départ → retour →
      clôture est jouable
- [ ] Le calcul de prix est déterministe, prouvé par un test sur l'ordre
- [ ] Le kilométrage du véhicule se met à jour au retour, par événement
- [ ] Une règle ArchUnit interdit la dépendance directe `rental` → `fleet`
- [ ] ADR 0004 consigne le choix de communication inter-contextes
- [ ] Les tests du domaine tournent toujours en moins de 2 secondes

---

## Interdits de ces deux week-ends

| Tentation | Pourquoi non |
|---|---|
| Ajouter de la pagination | Coupé assumé. Va dans BACKLOG.md. |
| Écrire les 5 politiques | Deux démontrent le pattern. Les trois autres sont de la répétition. |
| Soigner l'OpenAPI | WE12 s'il reste du temps. |
| Appeler `VehicleRepository` depuis `rental` | Couplage qui se paiera au WE8. |
| Commencer `sales` | WE8-9. Un contexte à la fois. |
| Ajouter Kafka | Janvier. Toujours. |

---

## Signal d'alerte

Si à la fin du WE7 le cycle complet n'est pas jouable de bout en bout,
**dis-le-moi avant d'attaquer le WE8**. La greffe de la vente sur un domaine
incomplet produirait deux contextes bancals au lieu d'un solide — et c'est la
partie du projet qui a le plus de valeur en entretien.

Dans ce cas on coupera dans l'observabilité, pas dans `sales`.
