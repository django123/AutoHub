-- =====================================================================
-- V1 : Socle de la base
-- =====================================================================
-- Cette migration ne cree aucune table metier : elle installe seulement
-- les extensions dont les phases suivantes auront besoin.
--
-- Pourquoi Flyway plutot que ddl-auto=update ?
--   1. ddl-auto derive du modele Java : on subit le schema au lieu de le
--      concevoir. Or les fonctionnalites PostgreSQL qui font l'interet de
--      ce projet (tstzrange, EXCLUDE USING gist) sont inexprimables en JPA.
--   2. Un schema versionne est rejouable a l'identique en CI, en local et
--      en production. ddl-auto ne l'est pas.
--   3. ddl-auto ne sait pas ecrire une migration de donnees.
--
-- Regle absolue : une migration livree n'est JAMAIS modifiee.
-- On corrige toujours en ajoutant V2, V3... Flyway stocke une empreinte
-- de chaque fichier et refusera de demarrer si l'un d'eux a change.
-- =====================================================================

-- btree_gist permet de melanger, dans un meme index GiST, des comparaisons
-- d'egalite classiques (vehicle_id WITH =) et des operateurs de plage
-- (period WITH &&). Sans cette extension, la contrainte d'exclusion du
-- week-end 5 serait impossible : GiST ne sait pas indexer un uuid seul.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Generation d'UUID cote base. Les identifiants seront generes par le
-- domaine Java (un agregat doit avoir son identite des sa creation, avant
-- toute persistance), mais cette extension reste utile pour les jeux de
-- donnees de demonstration.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Table technique de verification : confirme que Flyway s'execute bien.
-- Elle sera supprimee en V2, quand les vraies tables arriveront.
CREATE TABLE flyway_smoke_check (
    id          integer     PRIMARY KEY,
    checked_at  timestamptz NOT NULL DEFAULT now()
);

INSERT INTO flyway_smoke_check (id) VALUES (1);
