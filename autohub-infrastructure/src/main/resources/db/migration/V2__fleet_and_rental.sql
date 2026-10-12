-- =====================================================================
-- V2 : Parc de vehicules et locations
-- =====================================================================
-- Le coeur de cette migration est la contrainte rental_no_overlap : elle
-- rend la double reservation d'un vehicule PHYSIQUEMENT impossible, quel
-- que soit le nombre de threads, d'instances de l'application ou de
-- clients SQL qui ecrivent en meme temps.
--
-- Pourquoi en base et pas dans le code Java ?
--   La non-superposition n'est pas un invariant de l'agregat Rental : un
--   agregat ne voit que lui-meme. Un "SELECT ... chevauchement ?" suivi
--   d'un "INSERT" laisse une fenetre entre les deux ou une autre
--   transaction peut inserer. Seule la base voit toutes les transactions.
--
-- Choix des types temporels : voir ADR 0002 (tsrange, pas tstzrange).
-- Choix du mapping colonne generee : voir docs/sprint-we4-we5.md, bloc 2.2.
-- =====================================================================


-- ---------------------------------------------------------------------
-- La table temoin de V1 a rempli son office : Flyway s'execute.
-- On la supprime ici plutot que de modifier V1 (une migration livree
-- n'est jamais modifiee : Flyway refuserait de demarrer).
-- ---------------------------------------------------------------------
DROP TABLE flyway_smoke_check;


-- ---------------------------------------------------------------------
-- vehicle : le parc (contexte fleet)
-- ---------------------------------------------------------------------
-- Volontairement minimale : l'agregat Vehicle (week-end 3) n'est pas encore
-- ecrit. La table existe des maintenant parce que rental.vehicle_id doit
-- referencer quelque chose. Les colonnes manquantes (offres, etat) viendront
-- dans une migration ulterieure, avec l'agregat qui les justifie.
CREATE TABLE vehicle (
    id            uuid         PRIMARY KEY,
    -- Le VIN fait toujours 17 caracteres : char(17) documente la regle.
    -- UNIQUE car deux lignes avec le meme VIN seraient la meme voiture
    -- physique. L'index cree par UNIQUE sert la regle, pas une requete.
    vin           char(17)     NOT NULL UNIQUE,
    plate_number  varchar(9)   NOT NULL UNIQUE,
    brand         varchar(50)  NOT NULL,
    model         varchar(50)  NOT NULL,
    model_year    smallint     NOT NULL,
    -- Le domaine garantit que le kilometrage ne decroit jamais ; la base
    -- garantit au moins qu'il n'est pas negatif. Defense en profondeur :
    -- un script SQL de correction ne passe pas par le domaine.
    mileage_km    bigint       NOT NULL CHECK (mileage_km >= 0),
    status        varchar(20)  NOT NULL,
    -- Verrouillage optimiste : Hibernate ajoute "WHERE version = ?" a chaque
    -- UPDATE et l'incremente. Deux modifications concurrentes du meme
    -- vehicule (kilometrage mis a jour pendant qu'on le retire du parc) :
    -- la seconde echoue au lieu d'ecraser silencieusement la premiere.
    version       bigint       NOT NULL DEFAULT 0
);


-- ---------------------------------------------------------------------
-- rental : les locations (contexte rental)
-- ---------------------------------------------------------------------
CREATE TABLE rental (
    id                   uuid           PRIMARY KEY,

    -- Cle etrangere vers un autre contexte. Discutable dans un monolithe
    -- modulaire (elle couple les deux schemas), assumee ici : une location
    -- d'un vehicule inexistant est une corruption qu'aucun code ne doit
    -- pouvoir produire, et les deux contextes partagent la meme base.
    -- Pas d'index sur vehicle_id : aucune requete ne cherche "toutes les
    -- locations d'un vehicule" a ce jour, et on ne supprime jamais un
    -- vehicule (on le retire du parc). Voir aussi la contrainte d'exclusion
    -- plus bas, dont l'index GiST commence par vehicle_id.
    vehicle_id           uuid           NOT NULL REFERENCES vehicle (id),

    -- Pas de cle etrangere : il n'existe pas de contexte client.
    customer_id          uuid           NOT NULL,

    -- Deux colonnes simples, que JPA mappe sur deux LocalDateTime sans aucun
    -- type exotique. timestamp (sans fuseau) : heures nominales a l'agence,
    -- cf. ADR 0002.
    starts_at            timestamp      NOT NULL,
    ends_at              timestamp      NOT NULL,

    -- La plage, calculee par la base a partir des deux colonnes.
    -- GENERATED ... STORED : PostgreSQL la recalcule a chaque ecriture, et
    -- personne ne peut l'ecrire directement. JPA ne la mappe pas (ddl-auto
    -- "validate" ignore les colonnes non mappees), donc l'entite reste banale.
    -- '[)' : semi-ouvert, exactement la semantique de DateRange.overlaps().
    -- Verifie : tsrange(timestamp, timestamp, text) est IMMUTABLE, condition
    -- exigee pour une colonne generee comme pour un index.
    period               tsrange        GENERATED ALWAYS AS (tsrange(starts_at, ends_at, '[)')) STORED,

    -- Le statut est stocke par son nom d'enum Java. Le CHECK protege contre
    -- une faute de frappe dans un script SQL ou un renommage cote Java non
    -- suivi d'une migration -- dans les deux cas, mieux vaut une erreur
    -- immediate qu'une ligne qu'on ne pourra plus recharger.
    status               varchar(20)    NOT NULL CHECK (status IN (
                             'DRAFT', 'RESERVED', 'CONFIRMED', 'PICKED_UP',
                             'RETURNED', 'CLOSED', 'CANCELLED', 'EXPIRED')),

    -- Prix fige a la reservation. numeric et non float : voir Money.
    quoted_amount        numeric(12, 2) NOT NULL CHECK (quoted_amount > 0),
    quoted_currency      char(3)        NOT NULL,

    -- L'echeance de reservation, elle, est un instant absolu (timestamptz) :
    -- "dans 30 minutes" ne doit pas devenir "dans 90 minutes" la nuit du
    -- changement d'heure.
    reserved_until       timestamptz,

    -- Etats des lieux de depart et de retour, a plat. Un value object n'a pas
    -- d'identite : il n'a pas besoin de sa propre table.
    pickup_at            timestamp,
    pickup_mileage_km    bigint,
    pickup_fuel_percent  smallint,
    dropoff_at           timestamp,
    dropoff_mileage_km   bigint,
    dropoff_fuel_percent smallint,

    -- Verrouillage optimiste, voir vehicle.version.
    version              bigint         NOT NULL DEFAULT 0,

    -- Redondant avec DateRange, volontairement : une plage vide (fin = debut)
    -- ne chevauche RIEN aux yeux de l'operateur &&, et passerait donc a
    -- travers la contrainte d'exclusion. On l'interdit aussi ici.
    CONSTRAINT rental_period_not_empty CHECK (ends_at > starts_at),

    -- Un etat des lieux est complet ou absent, jamais a moitie rempli.
    CONSTRAINT rental_pickup_complete CHECK (
        num_nulls(pickup_at, pickup_mileage_km, pickup_fuel_percent) IN (0, 3)),
    CONSTRAINT rental_dropoff_complete CHECK (
        num_nulls(dropoff_at, dropoff_mileage_km, dropoff_fuel_percent) IN (0, 3)),

    -- -----------------------------------------------------------------
    -- LA contrainte du projet.
    -- -----------------------------------------------------------------
    -- Lecture : "il ne peut pas exister deux lignes dont vehicle_id est
    -- EGAL et dont les periodes se CHEVAUCHENT, parmi les lignes dont le
    -- statut bloque le vehicule".
    --
    -- * GiST, parce qu'un B-tree ne sait indexer qu'un ordre total : il
    --   repond a "egal / plus petit / plus grand", pas a "chevauche".
    -- * btree_gist (installee en V1) apprend a GiST a traiter l'egalite sur
    --   un uuid, pour combiner les deux criteres dans un seul index.
    -- * La clause WHERE rend la contrainte partielle : une location annulee,
    --   expiree, rendue ou au brouillon ne bloque plus le vehicule. Cette
    --   liste DOIT rester identique a RentalStatus.blockingStatuses() ; un
    --   test d'integration le verifie.
    -- * Non DEFERRABLE (defaut) : la violation est levee a l'INSERT, donc au
    --   flush, a l'interieur de l'adaptateur qui peut la traduire -- et non
    --   au COMMIT, hors de sa portee.
    -- * SQLState leve en cas de violation : 23P01 (exclusion_violation),
    --   distinct de 23505 (unique_violation).
    --
    -- L'index GiST cree par la contrainte sert aussi la future requete de
    -- disponibilite ("ce vehicule est-il libre sur cette periode ?", WE 6),
    -- a condition qu'elle filtre sur les memes statuts.
    CONSTRAINT rental_no_overlap EXCLUDE USING gist (
        vehicle_id WITH =,
        period     WITH &&
    ) WHERE (status IN ('RESERVED', 'CONFIRMED', 'PICKED_UP'))
);

-- Aucun autre index. La regle du sprint : pas d'index sans requete a nommer.
-- Les seules requetes ecrites aujourd'hui sont findById (servie par la cle
-- primaire) et l'INSERT controle par rental_no_overlap (servi par son index).
-- L'index partiel pour le traitement d'expiration
-- ("WHERE status = 'RESERVED' AND reserved_until < now()") arrivera avec ce
-- traitement, au week-end 7.
