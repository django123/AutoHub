# ADR 0001 — Architecture hexagonale en monolithe modulaire

- **Statut** : accepté
- **Date** : 2026-09-12
- **Décideur** : Kutemodojika

## Contexte

AutoHub gère la location et la vente de véhicules. Le projet a deux objectifs
simultanés : produire un système fonctionnel, et servir de support de révision
sur la POO, le DDD tactique et l'architecture hexagonale.

Trois contextes métier sont identifiés : `fleet` (le parc), `rental` (la
location), `sales` (la vente). Le contexte `sales` sera ajouté en phase 4,
soit deux mois après le démarrage — ce qui constitue un test grandeur nature
de l'extensibilité de l'architecture.

## Décision

**Monolithe modulaire**, découpé en quatre modules Maven correspondant aux
couches hexagonales, avec des frontières entre contextes métier vérifiées
automatiquement.

```
domain  <-  application  <-  infrastructure  <-  bootstrap
```

Les contextes métier communiquent exclusivement par événements de domaine,
jamais par appel direct.

## Alternatives écartées

### Microservices
Rejeté. Trois services impliqueraient un découpage transactionnel, du tracing
distribué et une orchestration de déploiement — pour une application à
utilisateur unique. Le coût opérationnel dépasserait largement le bénéfice
pédagogique, et masquerait l'objet réel de l'exercice, qui est la conception du
domaine.

### Monolithe en couches classique (`controller` / `service` / `repository`)
Rejeté. C'est la structure qui produit invariablement des entités JPA servant
d'objets métier et des « services » de 800 lignes. Elle n'enseigne rien sur la
protection des invariants.

### Cinq modules (avec un `shared` séparé)
Rejeté par pragmatisme. Un module dédié au noyau partagé se justifie quand
plusieurs applications le consomment. Ici, le noyau vit dans `domain` et ses
adaptateurs dans `infrastructure` — la démonstration architecturale est
intacte, avec un module et un `pom.xml` de moins à maintenir.

## Conséquences

### Positives
- Le module `domain` a un `pom.xml` sans aucune dépendance : c'est la preuve
  la plus courte et la plus convaincante que l'architecture est respectée.
- Maven refuse à la compilation toute dépendance inversée. La règle n'est pas
  une convention, c'est une impossibilité.
- Les tests du domaine tournent en millisecondes, donc on les lance souvent.
- Un découpage ultérieur en services reste possible : les frontières de
  contextes sont déjà posées.

### Négatives
- Quatre `pom.xml` à maintenir au lieu d'un.
- Naviguer entre les modules est légèrement plus lourd dans l'IDE.
- Un déploiement unique : impossible de mettre à l'échelle `rental`
  indépendamment. Sans objet à cette taille.

### Vérification
Les règles sont encodées dans `HexagonalArchitectureTest`. Une violation fait
échouer la CI. Cette classe est écrite au week-end 1, avant qu'il n'y ait quoi
que ce soit à protéger — une règle ajoutée après coup démarre rouge et finit
commentée.
