package com.ocarius.autohub.rental.application.service;

import com.ocarius.autohub.rental.application.port.in.ReserveRentalCommand;
import com.ocarius.autohub.rental.application.port.in.ReserveRentalUseCase;
import com.ocarius.autohub.rental.application.port.out.RentalRepository;
import com.ocarius.autohub.rental.application.port.out.VehicleCatalog;
import com.ocarius.autohub.rental.application.port.out.VehicleCatalog.CatalogVehicle;
import com.ocarius.autohub.rental.domain.exception.UnknownVehicleException;
import com.ocarius.autohub.rental.domain.exception.VehicleNotRentableException;
import com.ocarius.autohub.rental.domain.model.CustomerId;
import com.ocarius.autohub.rental.domain.model.Rental;
import com.ocarius.autohub.rental.domain.model.RentalId;
import com.ocarius.autohub.rental.domain.model.RentalPeriod;
import com.ocarius.autohub.rental.domain.model.RentalQuote;
import com.ocarius.autohub.rental.domain.pricing.PriceBreakdown;
import com.ocarius.autohub.rental.domain.pricing.PricingContext;
import com.ocarius.autohub.rental.domain.pricing.PricingService;
import com.ocarius.autohub.shared.application.port.out.EventPublisher;
import com.ocarius.autohub.shared.application.port.out.UnitOfWork;
import com.ocarius.autohub.shared.domain.VehicleId;

import java.time.Clock;
import java.util.UUID;

/**
 * Cas d'usage : reserver un vehicule.
 *
 * <h2>Deroulement, dans une seule transaction</h2>
 * <ol>
 *   <li>Lire le vehicule dans le catalogue : il doit exister et etre louable.</li>
 *   <li>Calculer le prix (tarification) et le figer dans un devis.</li>
 *   <li>Creer la location et la reserver ({@code Rental.reserve} : preavis).</li>
 *   <li><b>Faire expirer les reservations echues</b> qui occupent encore le
 *       creneau (voir plus bas).</li>
 *   <li>Enregistrer : la contrainte d'exclusion arbitre les reservations
 *       concurrentes, et l'adaptateur traduit un refus en
 *       {@code VehicleNotAvailableException}.</li>
 * </ol>
 *
 * <h2>Pourquoi expirer ici, en plus du traitement planifie</h2>
 * La contrainte d'exclusion filtre sur le <i>statut stocke</i>. Une reservation
 * echue depuis dix secondes reste {@code RESERVED} en base jusqu'au passage du
 * traitement planifie, et bloque le creneau pour rien. Plutot que de raccourcir
 * l'intervalle du traitement (on ne ferait que reduire la fenetre), on expire
 * les reservations echues qui genent <i>au moment ou elles genent</i>, dans la
 * meme transaction que la nouvelle reservation.
 *
 * <p>Ce cas d'usage n'orchestre que : aucune regle metier ici. Le preavis est
 * dans {@code Rental}, le prix dans {@code PricingService}, l'echeance dans
 * {@code Rental.expireIfOverdue}.
 */
public final class ReserveRentalService implements ReserveRentalUseCase {

    private final RentalRepository rentals;
    private final VehicleCatalog catalog;
    private final PricingService pricing;
    private final EventPublisher events;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    /**
     * @param rentals    stockage des locations
     * @param catalog    vue des vehicules louables
     * @param pricing    tarification, avec son ordre de politiques
     * @param events     publication des evenements de domaine
     * @param unitOfWork frontiere transactionnelle
     * @param clock      horloge de l'agence
     */
    public ReserveRentalService(RentalRepository rentals, VehicleCatalog catalog, PricingService pricing,
                                EventPublisher events, UnitOfWork unitOfWork, Clock clock) {
        this.rentals = rentals;
        this.catalog = catalog;
        this.pricing = pricing;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public UUID reserve(ReserveRentalCommand command) {
        VehicleId vehicleId = VehicleId.of(command.vehicleId());
        // Construite hors transaction : une periode invalide echoue sans ouvrir de connexion.
        RentalPeriod period = RentalPeriod.of(command.start(), command.end());

        return unitOfWork.execute(() -> {
            CatalogVehicle vehicle = catalog.findById(vehicleId)
                    .orElseThrow(() -> new UnknownVehicleException(vehicleId));
            if (!vehicle.rentable()) {
                throw new VehicleNotRentableException(vehicleId);
            }

            PriceBreakdown price = pricing.quote(new PricingContext(vehicle.dailyRate(), period));
            Rental rental = Rental.draft(RentalId.newId(), vehicleId, CustomerId.of(command.customerId()),
                    period, RentalQuote.of(vehicle.dailyRate(), price.total()));
            rental.reserve(clock);

            for (Rental stale : rentals.findOverdueReservationsOverlapping(vehicleId, period, clock.instant())) {
                if (stale.expireIfOverdue(clock)) {
                    Rentals.saveAndPublish(rentals, events, stale);
                }
            }

            Rentals.saveAndPublish(rentals, events, rental);
            return rental.id().value();
        });
    }
}
