-- =====================================================================
-- V3 : Agregat Vehicle complet, devis et penalite de location,
--      expiration des reservations
-- =====================================================================
-- V2 a ete livree : on ne la modifie pas, on la complete ici.
--
-- Aucune donnee de production n'existe a ce jour (l'application n'est pas
-- deployee). Les colonnes NOT NULL ajoutees recoivent malgre tout une
-- valeur de reprise explicite, pour qu'une base de developpement deja
-- peuplee migre sans erreur. Ces valeurs sont documentees une par une.
-- =====================================================================


-- ---------------------------------------------------------------------
-- vehicle : les colonnes que l'agregat Vehicle (WE3) justifie desormais
-- ---------------------------------------------------------------------
ALTER TABLE vehicle
    -- Etat physique, distinct du statut. Reprise : 'USED', l'hypothese la
    -- plus prudente pour un vehicule deja present dans le parc.
    ADD COLUMN condition           varchar(10)    NOT NULL DEFAULT 'USED',
    -- Offres a plat, une colonne par valeur possible. Deux booleens plutot
    -- qu'un tableau ou une table de jointure : l'ensemble est ferme (deux
    -- valeurs), et la requete de disponibilite filtre directement dessus.
    ADD COLUMN for_rent            boolean        NOT NULL DEFAULT false,
    ADD COLUMN for_sale            boolean        NOT NULL DEFAULT false,
    -- Tarif journalier de location (voir la javadoc de Vehicle sur sa place).
    ADD COLUMN daily_rate_amount   numeric(12, 2),
    ADD COLUMN daily_rate_currency char(3);

-- Reprise du tarif : 50 EUR, valeur arbitraire pour les seules lignes de
-- developpement. Une ligne sans tarif ne pourrait pas etre rechargee par le
-- domaine, qui exige un tarif strictement positif.
UPDATE vehicle SET daily_rate_amount = 50, daily_rate_currency = 'EUR' WHERE daily_rate_amount IS NULL;

ALTER TABLE vehicle
    ALTER COLUMN daily_rate_amount SET NOT NULL,
    ALTER COLUMN daily_rate_currency SET NOT NULL,
    -- Les defauts ne servaient qu'a la reprise : une insertion doit desormais
    -- fournir ces valeurs explicitement, comme le fait le domaine.
    ALTER COLUMN condition DROP DEFAULT,
    ALTER COLUMN for_rent DROP DEFAULT,
    ALTER COLUMN for_sale DROP DEFAULT,
    ADD CONSTRAINT vehicle_daily_rate_positive CHECK (daily_rate_amount > 0),
    -- Meme raison que rental.status (V2) : une faute de frappe dans un script
    -- SQL doit echouer tout de suite, pas rendre une ligne impossible a recharger.
    ADD CONSTRAINT vehicle_status_known CHECK (status IN (
        'ACQUIRED', 'IN_PREPARATION', 'IN_FLEET', 'IN_MAINTENANCE', 'SOLD', 'RETIRED')),
    ADD CONSTRAINT vehicle_condition_known CHECK (condition IN ('NEW', 'USED', 'DAMAGED'));


-- ---------------------------------------------------------------------
-- rental : devis fige et penalite de retard
-- ---------------------------------------------------------------------
ALTER TABLE rental
    -- Tarif journalier fige a la reservation : la penalite de retard se
    -- calcule dessus, et un changement de tarif ulterieur ne doit pas la
    -- modifier (voir RentalQuote). Meme devise que quoted_currency.
    ADD COLUMN daily_rate_amount           numeric(12, 2),
    -- Zero tant que le vehicule n'est pas revenu en retard.
    ADD COLUMN late_return_penalty_amount  numeric(12, 2) NOT NULL DEFAULT 0
        CHECK (late_return_penalty_amount >= 0);

-- Reprise du tarif fige : prix total divise par le nombre de jours entames.
-- Approximatif pour une location qui beneficiait d'une remise longue duree
-- (le tarif reconstitue est alors le tarif remise), acceptable pour des
-- donnees de developpement uniquement.
UPDATE rental
   SET daily_rate_amount = round(quoted_amount / greatest(1, ceil(extract(epoch FROM ends_at - starts_at) / 86400)), 2)
 WHERE daily_rate_amount IS NULL;

ALTER TABLE rental
    ALTER COLUMN daily_rate_amount SET NOT NULL,
    ADD CONSTRAINT rental_daily_rate_positive CHECK (daily_rate_amount > 0);


-- ---------------------------------------------------------------------
-- Index : le traitement d'expiration
-- ---------------------------------------------------------------------
-- Requete servie (RentalJpaRepository.findOverdueReservationIds) :
--   SELECT id FROM rental
--    WHERE status = 'RESERVED' AND reserved_until <= :now
--    ORDER BY reserved_until LIMIT :limit
-- Index PARTIEL : seules les reservations non confirmees y figurent. Elles
-- sont peu nombreuses a un instant donne (30 minutes de vie au plus), alors
-- que la table grossit indefiniment avec l'historique ; un index complet sur
-- reserved_until indexerait surtout des NULL.
CREATE INDEX rental_overdue_reservation_idx
    ON rental (reserved_until)
    WHERE status = 'RESERVED';

-- La recherche de reservations echues sur un vehicule et une periode
-- (findOverdueReservationsOverlapping) et la recherche de disponibilites
-- (JdbcVehicleCatalog) filtrent sur vehicle_id, period && et un statut
-- bloquant : elles sont servies par l'index GiST de rental_no_overlap (V2).
-- Aucun index supplementaire.
