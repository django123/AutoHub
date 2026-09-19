# Session Claude Code — Week-end 3
## Clôture de la phase 0 + agrégat `Vehicle`

> **Budget : 6 à 8 heures.** Les blocs sont ordonnés pour que tu puisses
> t'arrêter à la fin de n'importe lequel avec un dépôt qui compile et des
> tests verts.
>
> **Règle de la session :** Claude Code corrige l'infrastructure, mais
> **n'écrit pas une ligne de `Vehicle`**. Le domaine, c'est toi. Si tu
> déroges à ça, tu auras un bel agrégat que tu ne sauras pas défendre.

---

## Avant de lancer Claude (10 min)

```bash
cd autohub
git init && git add -A && git commit -m "chore: squelette phase 0"
docker compose up -d
mvn verify          # va probablement échouer — c'est normal
```

Note l'erreur, puis lance la session :

```bash
claude
```

---

## Bloc 0 — Faire compiler (45 min, timeboxé)

### Prompt 0.1

```
Le projet ne compile pas. Lance `mvn verify` et corrige UNIQUEMENT :
- les erreurs de compilation
- les versions de dépendances introuvables ou incompatibles
- les imports manquants ou erronés

Interdictions strictes :
- ne modifie aucune signature de méthode
- ne modifie aucun commentaire ni javadoc
- ne change aucune logique métier
- ne supprime aucun test

Si une correction demande un choix de conception, ARRÊTE-TOI et
demande-moi. Après chaque correction, dis-moi en une phrase ce qui était
cassé et pourquoi.
```

**Points de vigilance connus** — vérifie ces trois-là toi-même, ce sont les
candidats les plus probables :

1. La version de Spring Boot (`3.5.4` dans le POM parent) peut ne plus être
   la dernière. Vérifie sur `spring.io/projects/spring-boot`.
2. `flyway-database-postgresql` est un artefact séparé depuis Flyway 10 — si
   la migration échoue au démarrage, c'est lui.
3. `allowEmptyShould(true)` dans ArchUnit : l'API a pu bouger selon la
   version. Les règles `rental`/`sales` ne matchent rien aujourd'hui.

### Prompt 0.2 — si tu débordes des 45 minutes

```
On dépasse le temps alloué. Liste-moi ce qui reste cassé, classé par
effort de correction estimé. Je veux décider quoi couper plutôt que
continuer à corriger à l'aveugle.
```

> **Discipline :** si `ApplicationSmokeTest` résiste, désactive-le avec
> `@Disabled("à réparer WE4")` et avance. Un test d'intégration cassé ne doit
> pas bloquer trois heures de travail sur le domaine.

---

## Bloc 1 — Valider ce que tu as reçu (45 min)

Tu n'as pas écrit le code de la phase 0. Avant de construire dessus, assure-toi
que tu le comprends — sinon toute la suite repose sur du vide.

### Prompt 1.1 — Revue critique

```
Relis tout le projet et réponds précisément à ces quatre questions.
Ne corrige rien.

1. Le module autohub-domain a-t-il réellement zéro dépendance ? Vérifie le
   pom.xml ET les imports de chaque fichier source, un par un.

2. Si j'ajoute une annotation @Entity dans le domaine, quelle règle ArchUnit
   échoue exactement, et quel message affiche-t-elle ? Décris le mécanisme,
   ne te contente pas de l'affirmer.

3. Y a-t-il une divergence entre la sémantique de DateRange.overlaps() et
   celle de l'opérateur && de PostgreSQL sur un tstzrange '[)' ? Donne un
   cas limite concret qui prouverait ta réponse.

4. Quelle est la faiblesse la plus sérieuse de ce squelette ? Une seule,
   la plus grave. Sois direct.
```

### Prompt 1.2 — Interrogation

```
Interroge-moi sur le code de la phase 0, comme un examinateur en entretien
technique. Cinq questions, UNE À LA FOIS, en attendant ma réponse avant la
suivante.

Couvre au minimum :
- le piège du scale de BigDecimal et pourquoi Money le normalise
- pourquoi les intervalles sont semi-ouverts
- pourquoi pullDomainEvents() vide la liste au lieu de la retourner
- pourquoi l'agrégat n'a aucun moyen de publier ses propres événements

Si ma réponse est approximative, ne me félicite pas : dis-moi ce qui manque
et reformule la question.
```

> Si tu bloques sur plus de deux questions sur cinq, relis le README avant
> d'attaquer le bloc 2. Ce n'est pas du temps perdu.

---

## Bloc 2 — Concevoir `Vehicle` sans coder (1 h)

### Prompt 2.1 — Mode socratique

```
Je vais modéliser l'agrégat Vehicle du contexte fleet. NE CODE RIEN.

Pose-moi les questions qui vont me forcer à trancher les vraies décisions
de conception. UNE question à la fois, attends ma réponse.

Couvre au minimum :
- la frontière de l'agrégat : qu'est-ce qui est dedans, qu'est-ce qui est
  une simple référence par identifiant
- ce qui mérite un value object et ce qui n'en mérite pas
- les invariants qui appartiennent à l'agrégat, par opposition à ceux qui
  le dépassent et devront vivre ailleurs
- la machine à états : quelles transitions doivent être impossibles

Si une de mes réponses crée une incohérence avec une réponse précédente,
signale-la immédiatement plutôt que de continuer.
```

**Réponses que tu dois savoir défendre à la fin de ce bloc :**

- Pourquoi `Vin` est un value object et pas un `String`
- Pourquoi `mileage` ne peut que croître, et où cet invariant est protégé
- Pourquoi « on ne peut pas retirer l'offering `FOR_RENT` s'il existe des
  locations futures » ne peut **pas** vivre dans l'agrégat `Vehicle`

### Prompt 2.2 — Consigner la décision

```
Résume les décisions qu'on vient de prendre sous forme d'ADR, au format des
fichiers existants dans docs/adr/. Numérote-le 0002.

Inclus obligatoirement une section "Alternatives écartées" avec ce que j'ai
envisagé puis abandonné, et pourquoi.
```

---

## Bloc 3 — Écrire `Vehicle` toi-même (2 à 3 h)

**Ferme Claude Code pendant ce bloc.** Ou au minimum, ne lui demande rien
d'autre que ce qui suit.

### Prompt 3.1 — La liste de tests, et rien d'autre

```
Je vais écrire les tests de Vehicle moi-même.

Donne-moi UNIQUEMENT la liste des cas à couvrir, sous forme de noms de
méthodes en français, groupés par bloc @Nested. Aucun corps de méthode,
aucune assertion, aucun import.

Inclus les cas limites auxquels je n'ai probablement pas pensé, et marque
d'un astérisque ceux que la plupart des développeurs oublient.
```

Puis tu codes, dans cet ordre :

1. Les value objects — `VehicleId`, `Vin`, `PlateNumber`, `Mileage`, `VehicleModel`
2. L'enum `VehicleStatus` avec ses transitions autorisées
3. Les tests des value objects (ils doivent être verts avant d'aller plus loin)
4. L'agrégat `Vehicle`
5. Les tests de l'agrégat
6. Les événements de domaine

### Prompt 3.2 — Si tu bloques plus de 30 minutes

```
Je bloque depuis 30 minutes sur : [décris le problème].

Ne me donne pas la solution. Pose-moi trois questions qui vont me permettre
de la trouver. Si mes réponses restent à côté, donne-moi un indice — toujours
pas la solution.
```

---

## Bloc 4 — Revue critique (45 min)

### Prompt 4.1

```
Je viens d'écrire l'agrégat Vehicle et ses value objects. Fais une revue
critique selon cette grille. NE CORRIGE RIEN.

1. Un invariant peut-il être contourné ? Écris le code appelant qui
   laisserait l'agrégat dans un état invalide.
2. Des getters exposent-ils l'état interne au lieu d'un comportement ?
   (violation Tell-Don't-Ask)
3. Des chaînes d'appels à plus d'un point ? (Loi de Déméter)
4. Une collection interne est-elle exposée sans copie défensive ?
5. La machine à états autorise-t-elle une transition qui ne devrait pas
   exister ? Donne la séquence d'appels qui le prouve.
6. Ai-je écrit des règles métier dans les tests plutôt que dans l'agrégat ?

Pour chaque problème : cite la ligne, décris la conséquence concrète en
production, propose une correction. Mais ne l'applique pas.
```

### Prompt 4.2 — Après avoir corrigé toi-même

```
J'ai appliqué les corrections. Vérifie qu'elles sont justes et qu'aucune
n'a introduit un nouveau problème.

Dis-moi surtout : parmi les problèmes que tu as trouvés, lesquels j'aurais
pu détecter seul, et à quel signal ? Je veux savoir quoi surveiller la
prochaine fois.
```

---

## Bloc 5 — Déployer (45 min, non négociable)

### Prompt 5.1

```
Je veux déployer l'application, même sans fonctionnalité métier exposée.

Compare trois options gratuites ou quasi gratuites pour une app Spring Boot
avec PostgreSQL managé : facilité de mise en route, limites du palier
gratuit, et ce qui casse quand le projet grossit.

Recommande-en une pour mon cas, puis génère le Dockerfile multi-stage et
le fichier de configuration nécessaire.
```

> **Pourquoi maintenant et pas en décembre :** la dette de déploiement ne se
> voit pas, elle grossit, et elle se découvre toujours au pire moment. Une
> coquille vide déployée aujourd'hui vaut mieux qu'un projet parfait déployé
> jamais.

---

## Clôture de session (15 min)

### Prompt 6.1

```
Fin de session. Fais un bilan en quatre points :

1. Qu'est-ce qui a été réellement terminé aujourd'hui ?
2. Qu'est-ce qui est à moitié fait et risque de me piéger au prochain
   week-end ?
3. Les règles ArchUnit passent-elles encore ? Une nouvelle règle
   mériterait-elle d'être ajoutée maintenant que fleet existe ?
4. Si je devais présenter ce code en entretien, quelle partie me mettrait
   en difficulté ?

Sois critique, je n'ai pas besoin d'être rassuré.
```

```bash
git add -A
git commit -m "feat(fleet): agrégat Vehicle avec invariants et machine à états"
git push
```

---

## Definition of Done

- [ ] `mvn verify` est vert en local
- [ ] La CI GitHub Actions est verte sur `main`
- [ ] L'application est **déployée** et `/actuator/health` répond en ligne
- [ ] `Vehicle` et ses value objects sont couverts par des tests sans Spring
- [ ] Les tests du domaine s'exécutent en moins de 2 secondes
- [ ] `docs/adr/0002` consigne les décisions de modélisation
- [ ] Tu sais expliquer à voix haute pourquoi `Vin` est un value object

---

## Ce qui est interdit cette semaine

| Tentation | Pourquoi c'est non |
|---|---|
| Commencer `Rental` | C'est le week-end 4. Un agrégat à la fois. |
| Ajouter Kafka | Janvier. Rien à transporter aujourd'hui. |
| Créer l'API REST | Week-end 6. Le domaine d'abord. |
| Écrire les entités JPA | Week-end 5, avec la migration. |
| Laisser Claude écrire `Vehicle` | Tu perdrais l'essentiel de la session. |

Toute autre idée qui surgit va dans `BACKLOG.md`, pas dans le code.
