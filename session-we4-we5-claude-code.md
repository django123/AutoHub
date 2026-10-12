# Session Claude Code — Sprint WE4 + WE5
## Agrégat `Rental` et contrainte d'exclusion PostgreSQL

> **Format : sprint sur deux jours.** Jour 1 = domaine pur, jour 2 =
> persistance. Ne les mélange pas : écrire le mapping JPA pendant que tu
> conçois l'agrégat est le meilleur moyen de laisser l'ORM dicter ton modèle.
>
> **C'est le week-end le plus important du projet.** Tout ce qui suit
> (API, tarification, vente) est de l'assemblage. Ici, tu travailles les deux
> choses qui font la valeur réelle d'AutoHub : un agrégat qui protège ses
> invariants, et une base qui rend le double-booking physiquement impossible.
>
> **Discipline de rattrapage :** le périmètre ci-dessous est déjà réduit.
> Si tu débordes, coupe dans le jour 2 (moins de politiques, moins
> d'endpoints) — jamais dans le test de concurrence du bloc 2.4.

---

# JOUR 1 — L'agrégat `Rental` (6-7 h)

## Bloc 1.0 — Reprise de contexte (15 min)

```
Relis le projet et fais-moi un état des lieux en 10 lignes maximum :
qu'est-ce qui est terminé, qu'est-ce qui est à moitié fait, et qu'est-ce
qui risque de me bloquer aujourd'hui.

Vérifie en particulier que `mvn verify` passe et que les règles ArchUnit
sont toujours vertes. Si quelque chose est cassé, corrige uniquement ça.
```

---

## Bloc 1.1 — La frontière d'agrégat (45 min) 🧠

C'est la décision la plus structurante de la journée, et celle que la plupart
des gens ratent.

```
Je vais modéliser l'agrégat Rental. NE CODE RIEN.

Pose-moi UNE question à la fois, attends ma réponse, et enchaîne.

Thèmes à couvrir impérativement :

1. Rental doit-il contenir un objet Vehicle, ou seulement un VehicleId ?
   Fais-moi argumenter les deux positions avant de conclure, et demande-moi
   ce qui se passe en base dans chaque cas quand je charge 100 locations.

2. L'invariant "un véhicule ne peut pas être loué deux fois sur des périodes
   qui se chevauchent" : est-ce un invariant de l'agrégat Rental ?
   Si je réponds oui, démonte ma réponse avec un scénario concurrent précis.

3. Où vit le prix d'une location ? Calculé à la lecture, ou figé à la
   réservation ? Quelles sont les conséquences métier de chaque choix ?

4. VehicleHandover (état des lieux au départ et au retour) : entité interne
   de l'agrégat, ou value object ?

Si une de mes réponses contredit une précédente, signale-le tout de suite.
```

**Ce que tu dois avoir compris à la fin de ce bloc** — si ce n'est pas le cas,
ne passe pas à la suite :

> La non-superposition des locations **n'est pas** un invariant de l'agrégat
> `Rental`. Un agrégat ne peut protéger que ce qu'il voit à l'intérieur de sa
> propre transaction. Deux instances de `Rental` créées simultanément par deux
> threads n'ont aucun moyen de se connaître.
>
> Cet invariant **traverse les agrégats** : il appartient à la base de données.
> C'est précisément pour cela que le jour 2 existe.

---

## Bloc 1.2 — La machine à états (45 min) 🧠

```
Rental a cette machine à états :

DRAFT → RESERVED → CONFIRMED → PICKED_UP → RETURNED → CLOSED
avec CANCELLED et EXPIRED comme états terminaux.

Compare trois implémentations en Java 21 :
(a) un enum avec méthodes abstraites par constante
(b) des sealed interfaces + pattern matching sur switch
(c) une table statique de transitions autorisées (Map<Status, Set<Status>>)

Pour chacune, évalue : lisibilité, testabilité, facilité de persistance en
base, coût d'ajout d'un état, et capacité à porter des données propres à un
état (par exemple reservedUntil, qui n'a de sens qu'en RESERVED).

Recommande-en une et argumente. NE CODE PAS.
```

Puis consigne la décision :

```
Écris l'ADR 0003 sur ce choix, au format des fichiers de docs/adr/.
Section "Alternatives écartées" obligatoire.
```

---

## Bloc 1.3 — Tu écris `Rental` (3 h) ⌨️

**Ferme Claude Code**, ou limite-toi au prompt ci-dessous.

```
Je vais écrire Rental moi-même.

Donne-moi UNIQUEMENT la liste des tests à écrire : noms de méthodes en
français, groupés par bloc @Nested. Aucun corps, aucune assertion, aucun
import.

Marque d'un astérisque les cas que la plupart des développeurs oublient.
Inclus obligatoirement des cas sur :
- les transitions interdites (cancel après pickUp, etc.)
- le kilométrage au retour inférieur à celui du départ
- l'expiration d'une réservation
- un retour avant la date prévue
```

Ordre d'écriture recommandé :

1. `RentalId`, `CustomerId` — deux records d'une ligne
2. `RentalPeriod` — enveloppe `DateRange` en y ajoutant les règles propres à
   la location (durée minimale, durée maximale, préavis)
3. `RentalStatus` + les transitions
4. Les tests de `RentalStatus` (verts avant de continuer)
5. `VehicleHandover`
6. L'agrégat `Rental`
7. Les tests de `Rental`
8. Les événements : `RentalReserved`, `RentalConfirmed`, `RentalCancelled`,
   `RentalExpired`, `VehiclePickedUp`, `VehicleReturned`

### Si tu bloques plus de 30 minutes

```
Je bloque depuis 30 minutes sur : [décris].

Ne me donne pas la solution. Pose-moi trois questions qui vont me permettre
de la trouver. Si mes réponses restent à côté, un indice — toujours pas la
solution.
```

---

## Bloc 1.4 — Revue critique (45 min) 🔍

```
J'ai écrit Rental. Revue critique selon cette grille. NE CORRIGE RIEN.

1. Écris le code appelant qui laisserait Rental dans un état invalide.
   Si tu n'y arrives pas, dis-le — c'est bon signe.
2. Des getters exposent-ils l'état interne au lieu d'un comportement ?
3. Des chaînes à plus d'un point ? (Loi de Déméter)
4. Une collection interne exposée sans copie défensive ?
5. Quelle séquence d'appels prouve qu'une transition interdite est possible ?
6. Des règles métier ont-elles atterri dans mes tests au lieu de l'agrégat ?
7. L'agrégat dépend-il de l'horloge système plutôt que d'un Clock injecté ?

Pour chaque problème : la ligne, la conséquence concrète en production,
la correction proposée. Mais ne l'applique pas.
```

Puis, après tes corrections :

```
Parmi les problèmes que tu as trouvés, lesquels j'aurais pu détecter seul,
et à quel signal ? Je veux savoir quoi surveiller la prochaine fois.
```

**Fin du jour 1 :**

```bash
mvn -pl autohub-domain test   # doit tourner en moins de 2 secondes
git commit -am "feat(rental): agrégat Rental, machine à états et invariants"
```

---

# JOUR 2 — La persistance et la contrainte d'exclusion (7-8 h)

## Bloc 2.1 — Comprendre GiST avant d'écrire du SQL (1 h) 🧠

```
Explique-moi EXCLUDE USING gist en partant de zéro, dans cet ordre :

1. Pourquoi un index B-tree est structurellement incapable d'indexer une
   plage de valeurs
2. Ce que GiST fait différemment
3. Pourquoi l'extension btree_gist est nécessaire pour combiner
   "vehicle_id WITH =" et "period WITH &&" dans la même contrainte
4. Ce qui se passe EXACTEMENT, au niveau du moteur, quand deux transactions
   concurrentes insèrent des plages qui se chevauchent : qui attend, qui
   échoue, et à quel moment précis

Pour chaque point, donne-moi des commandes SQL que je peux exécuter dans
psql pour vérifier ton affirmation. Je veux constater, pas te croire.
```

Puis, exécute-les réellement :

```bash
docker compose exec postgres psql -U autohub -d autohub
```

---

## Bloc 2.2 — Le problème du mapping `tstzrange` ↔ JPA (1 h) 🧠

C'est le piège technique du week-end. Hibernate ne connaît pas nativement les
types range de PostgreSQL.

```
Hibernate ne sait pas mapper tstzrange nativement. J'ai identifié trois
approches. Compare-les, et VÉRIFIE chacune dans psql avant de conclure —
ne te fie pas à ta mémoire.

(a) Mapper tstzrange directement via un UserType Hibernate personnalisé

(b) Deux colonnes timestamptz (starts_at, ends_at) + une colonne générée
    STORED contenant tstzrange(starts_at, ends_at, '[)'), la contrainte
    d'exclusion portant sur la colonne générée. JPA ne voit que les deux
    colonnes simples.

(c) Deux colonnes timestamptz, la contrainte d'exclusion portant directement
    sur l'EXPRESSION tstzrange(starts_at, ends_at, '[)').

Questions auxquelles tu dois répondre par l'expérience, pas par déduction :
- (b) et (c) sont-elles réellement acceptées par PostgreSQL ? Une contrainte
  d'exclusion exige un index ; une colonne générée et une expression sont-elles
  indexables par GiST ici ?
- L'expression de (c) est-elle considérée IMMUTABLE ? Sinon, PostgreSQL la
  refusera.
- Dans quelle mesure (a) contamine-t-elle l'entité JPA, et donc le mapper ?

Recommande-en une pour mon cas, sachant que je veux que l'entité JPA reste
la plus banale possible.
```

> **Pourquoi ça compte.** Si tu choisis (a), ton entité JPA porte un type
> exotique qui remontera dans le mapper, et la frontière domaine/persistance
> s'épaissit. Si (b) ou (c) fonctionne, ton entité JPA n'a que deux
> `Instant` — et toute l'intelligence reste en base, là où elle doit être.

---

## Bloc 2.3 — La migration (1 h) 🔨

Génération acceptable ici : c'est du SQL, pas du métier.

```
Écris la migration Flyway V2__fleet_and_rental.sql.

Contenu :
- table vehicle (avec colonne version pour le verrouillage optimiste)
- table rental
- la contrainte d'exclusion selon l'approche retenue au bloc précédent,
  active UNIQUEMENT sur les statuts RESERVED, CONFIRMED, PICKED_UP
- les index sur les colonnes réellement interrogées

Pour chaque index, justifie en commentaire la requête qu'il sert.
Si tu ne peux pas nommer la requête, ne crée pas l'index.

Commente chaque bloc SQL en expliquant le POURQUOI, pas le QUOI.
Supprime la table flyway_smoke_check créée en V1.
```

Puis vérifie que la contrainte mord :

```
Donne-moi une séquence de commandes psql qui prouve, à la main, que la
contrainte fonctionne : j'insère une location, puis une seconde qui
chevauche, et je dois voir l'erreur. Montre-moi aussi le cas adjacent
(fin = début) qui doit, lui, PASSER.
```

---

## Bloc 2.4 — Le test de concurrence (1 h 30) 🧠 puis ⌨️

**Le test le plus instructif du projet. Ne le coupe pas, même en retard.**

```
Je veux écrire le test : 50 threads réservent le même véhicule sur la même
période, exactement 1 doit réussir.

NE L'ÉCRIS PAS. Explique-moi :

1. Pourquoi un ExecutorService avec 50 submit() ne produit PAS une vraie
   concurrence, et quel mécanisme ajouter pour que les 50 threads partent
   réellement au même instant
2. Quel SQLState PostgreSQL remonte lors d'une violation de contrainte
   d'exclusion, et comment le distinguer d'une violation de clé unique
3. En quelle exception Hibernate puis Spring l'enveloppent successivement
4. Où traduire cette exception en exception de domaine — et pourquoi
   surtout pas dans le module domain
5. Pourquoi ce test doit tourner sur Testcontainers et serait inutile sur H2
```

Ensuite, **tu l'écris**. Résultat attendu : 1 succès, 49 `VehicleNotAvailableException`.

> **Si le test passe du premier coup, méfie-toi.** Vérifie qu'il échoue bien
> quand tu retires la contrainte d'exclusion de la migration. Un test qui ne
> peut pas échouer ne teste rien.

---

## Bloc 2.5 — Le mapper et l'adaptateur (1 h 30) ⌨️ puis 🔍

Écris toi-même `RentalJpaEntity`, `RentalMapper`, `RentalRepositoryAdapter`.
Puis :

```
Relis mon mapper et mon adaptateur de repository. Vérifie :

1. Le domaine peut-il fuir vers l'entité JPA, ou l'inverse ?
2. La reconstruction d'un agrégat depuis la base passe-t-elle par les
   constructeurs métier — qui revalident les invariants — ou les
   contourne-t-elle ? Si elle les contourne : bug ou choix défendable ?
   Argumente les deux positions avant de trancher.
3. Où vit le champ `version` du verrouillage optimiste : dans l'agrégat de
   domaine, dans l'entité JPA, ou dans les deux ? Quelles sont les
   conséquences de chaque option sur la pureté du domaine ?
4. L'adaptateur traduit-il bien TOUTES les exceptions techniques en
   exceptions de domaine, ou certaines fuient-elles vers la couche
   application ?

Cite les lignes. Ne corrige pas.
```

---

## Clôture du sprint (30 min)

```
Fin de sprint. Bilan en quatre points, sois critique :

1. Qu'est-ce qui est réellement terminé ?
2. Qu'est-ce qui est à moitié fait et va me piéger au prochain week-end ?
3. Les règles ArchUnit passent-elles encore ? Faut-il en ajouter une
   maintenant que la persistance existe ?
4. En entretien, quelle partie de ce code me mettrait en difficulté ?

Ensuite, mets à jour le tableau de feuille de route du README.
```

```bash
mvn verify
git commit -am "feat(rental): persistance, contrainte d'exclusion GiST, test de concurrence"
git push
```

---

## Definition of Done

- [ ] `Rental` et sa machine à états sont testés **sans Spring**
- [ ] Les tests du domaine tournent en moins de 2 secondes
- [ ] La migration V2 crée la contrainte d'exclusion
- [ ] Tu as prouvé **à la main dans psql** qu'un chevauchement est rejeté et
      qu'une réservation adjacente passe
- [ ] Le test 50 threads → 1 succès est vert
- [ ] Ce test échoue si tu retires la contrainte (vérifié)
- [ ] `RentalJpaEntity` et `Rental` sont deux classes distinctes
- [ ] ADR 0003 consigne le choix de machine à états
- [ ] `mvn verify` et la CI sont verts
- [ ] Tu sais expliquer pourquoi la non-superposition n'est **pas** un
      invariant de l'agrégat

---

## Interdits de ce sprint

| Tentation | Pourquoi non |
|---|---|
| Commencer l'API REST | Week-end 6. Le domaine et la persistance d'abord. |
| Écrire les 5 politiques de tarification | Réduit à 2, et c'est pour le WE suivant. |
| Ajouter Kafka | Janvier. Toujours rien à transporter. |
| Sauter le test de concurrence | C'est la seule chose de ce sprint qu'un autre développeur ne saurait pas faire. |
| Laisser Claude écrire `Rental` | Tu perdrais l'intégralité de la valeur du jour 1. |

Toute autre idée va dans `BACKLOG.md`.
