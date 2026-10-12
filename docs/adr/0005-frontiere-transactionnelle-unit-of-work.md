# ADR 0005 — Frontière transactionnelle : un port `UnitOfWork` dans la couche application

- **Statut** : accepté
- **Date** : 2026-10-12
- **Décideur** : Kutemodojika

## Contexte

Avec les cas d'usage (WE6), il faut décider **où commence et où finit une
transaction**. Le cas de référence est la réservation. Faire expirer une
réservation échue qui gêne, enregistrer la nouvelle, puis publier leurs
événements : ces étapes doivent réussir ou échouer ensemble.

La réponse habituelle, `@Transactional` sur le service d'application, est
impossible ici. Le module `autohub-application` n'a pas Spring sur son
classpath (ADR 0001), et c'est voulu : un cas d'usage s'instancie avec `new`
dans un test, avec des fakes.

## Décision

**La couche application déclare un port sortant `UnitOfWork`**
(`execute(Supplier)` et `run(Runnable)`). Chaque cas d'usage délimite lui-même
sa transaction dans son code. L'infrastructure l'implémente avec un
`TransactionTemplate` (`SpringUnitOfWork`). En test, `DirectUnitOfWork`
exécute simplement le travail et compte les transactions ouvertes.

Les adaptateurs de repository exigent une transaction existante
(`Propagation.MANDATORY` sur `save`) : ils participent à celle du cas d'usage,
sans jamais en ouvrir une.

## Alternatives écartées

### `@Transactional` sur le contrôleur
La transaction durerait pendant la sérialisation JSON. Un cas d'usage appelé
par un job ou un listener ne serait plus transactionnel. Et le contrôleur
déciderait d'une règle de cohérence qui appartient au cas d'usage.

### `@Transactional` sur l'adaptateur de repository
Chaque `save` serait sa propre transaction. L'expiration de la réservation
échue et l'enregistrement de la nouvelle pourraient réussir l'une sans
l'autre. Surtout, le verrouillage optimiste ne protégerait plus rien, puisque
le chargement et la sauvegarde auraient lieu dans deux transactions distinctes.

### Ajouter `spring-tx` à la couche application
On pourrait le faire pour la seule annotation. Mais la frontière « pas de
framework dans l'application » cesserait d'être une règle simple et
vérifiable. Et les tests sans Spring perdraient leur transaction : une
annotation n'agit que dans un conteneur.

### Décorateurs transactionnels dans l'infrastructure
On aurait enveloppé chaque bean de cas d'usage dans un proxy transactionnel
déclaré dans la configuration. La frontière n'apparaîtrait alors plus du tout
dans le code du cas d'usage, alors que c'est lui qui la connaît.

## Conséquences

### Positives
- La frontière transactionnelle se lit dans le cas d'usage, là où elle est
  décidée.
- Les tests des cas d'usage vérifient aussi la découpe en transactions. Le
  traitement d'expiration en ouvre une par réservation, et un test le prouve.
- Le domaine reste ignorant des transactions : un agrégat est une frontière de
  cohérence, pas de transaction.

### Négatives
- Un cas d'usage qui oublie d'appeler `UnitOfWork` échoue à l'exécution
  (`IllegalTransactionStateException` du repository), pas à la compilation.
  Les tests d'intégration le détecteraient.
- Un niveau d'indentation de plus dans chaque cas d'usage (la lambda).
