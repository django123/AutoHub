package com.ocarius.autohub.rental.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representation JPA d'une ligne de la table {@code rental}.
 *
 * <h2>Une classe volontairement banale</h2>
 * Ce n'est <b>pas</b> l'objet metier : c'est un sac de colonnes, avec un
 * constructeur vide et des accesseurs, parce que c'est ce qu'Hibernate exige.
 * Ces exigences restent confinees ici ; l'agregat {@code Rental} ne les subit
 * pas. Le passage de l'un a l'autre est le travail exclusif de
 * {@link RentalMapper}.
 *
 * <p>Trois choix pour rester banal :
 * <ul>
 *   <li><b>Pas de type range.</b> La colonne {@code period tsrange} est generee
 *       par PostgreSQL a partir de {@code starts_at} et {@code ends_at}, et n'est
 *       pas mappee ici. L'entite ne voit que deux {@link LocalDateTime}.</li>
 *   <li><b>Le statut est une {@code String}</b>, pas l'enum du domaine. Importer
 *       {@code RentalStatus} ici lierait silencieusement le nom des constantes
 *       Java au contenu de la base : un renommage dans le domaine casserait la
 *       relecture des lignes existantes sans qu'aucun compilateur ne le signale.
 *       La conversion est explicite dans le mapper.</li>
 *   <li><b>Aucune logique.</b> Pas de methode metier, pas de validation : un
 *       {@code if} ici serait une regle metier au mauvais endroit.</li>
 * </ul>
 *
 * <h2>Le champ {@code version}</h2>
 * Il vit uniquement ici, pas dans le domaine. Le verrouillage optimiste est un
 * mecanisme de stockage ; l'agregat n'a pas a savoir qu'il existe. La
 * contrepartie est expliquee dans {@link RentalRepositoryAdapter}.
 */
@Entity
@Table(name = "rental")
public class RentalJpaEntity {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "quoted_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal quotedAmount;

    /**
     * {@code @JdbcTypeCode(CHAR)} : sans lui, Hibernate associe une {@code String}
     * a un varchar, et la validation du schema refuse la colonne char(3).
     * ({@code columnDefinition} ne suffit pas : la validation compare le type
     * JDBC, pas le texte DDL.)
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "quoted_currency", nullable = false, length = 3)
    private String quotedCurrency;

    @Column(name = "daily_rate_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRateAmount;

    @Column(name = "late_return_penalty_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateReturnPenaltyAmount;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "pickup_at")
    private LocalDateTime pickupAt;

    @Column(name = "pickup_mileage_km")
    private Long pickupMileageKm;

    @Column(name = "pickup_fuel_percent")
    private Short pickupFuelPercent;

    @Column(name = "dropoff_at")
    private LocalDateTime dropoffAt;

    @Column(name = "dropoff_mileage_km")
    private Long dropoffMileageKm;

    @Column(name = "dropoff_fuel_percent")
    private Short dropoffFuelPercent;

    /**
     * Gere exclusivement par Hibernate. {@code Long} et non {@code long} : une
     * valeur {@code null} signale a Spring Data une entite nouvelle (INSERT),
     * une valeur presente une entite existante (UPDATE ... WHERE version = ?).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Exige par JPA. {@code protected} : seul Hibernate (et ce paquet) l'utilise. */
    protected RentalJpaEntity() {
    }

    /** Cree une entite vide pour une location qui n'existe pas encore en base. */
    RentalJpaEntity(UUID id) {
        this.id = id;
    }

    // Accesseurs de paquet : seuls le mapper et l'adaptateur, dans ce meme
    // paquet, manipulent l'entite. Rien a l'exterieur de la persistance ne
    // doit pouvoir y toucher.

    UUID getId() { return id; }

    UUID getVehicleId() { return vehicleId; }
    void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    UUID getCustomerId() { return customerId; }
    void setCustomerId(UUID customerId) { this.customerId = customerId; }

    LocalDateTime getStartsAt() { return startsAt; }
    void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }

    LocalDateTime getEndsAt() { return endsAt; }
    void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }

    String getStatus() { return status; }
    void setStatus(String status) { this.status = status; }

    BigDecimal getQuotedAmount() { return quotedAmount; }
    void setQuotedAmount(BigDecimal quotedAmount) { this.quotedAmount = quotedAmount; }

    String getQuotedCurrency() { return quotedCurrency; }
    void setQuotedCurrency(String quotedCurrency) { this.quotedCurrency = quotedCurrency; }

    BigDecimal getDailyRateAmount() { return dailyRateAmount; }
    void setDailyRateAmount(BigDecimal dailyRateAmount) { this.dailyRateAmount = dailyRateAmount; }

    BigDecimal getLateReturnPenaltyAmount() { return lateReturnPenaltyAmount; }
    void setLateReturnPenaltyAmount(BigDecimal amount) { this.lateReturnPenaltyAmount = amount; }

    Instant getReservedUntil() { return reservedUntil; }
    void setReservedUntil(Instant reservedUntil) { this.reservedUntil = reservedUntil; }

    LocalDateTime getPickupAt() { return pickupAt; }
    void setPickupAt(LocalDateTime pickupAt) { this.pickupAt = pickupAt; }

    Long getPickupMileageKm() { return pickupMileageKm; }
    void setPickupMileageKm(Long pickupMileageKm) { this.pickupMileageKm = pickupMileageKm; }

    Short getPickupFuelPercent() { return pickupFuelPercent; }
    void setPickupFuelPercent(Short pickupFuelPercent) { this.pickupFuelPercent = pickupFuelPercent; }

    LocalDateTime getDropoffAt() { return dropoffAt; }
    void setDropoffAt(LocalDateTime dropoffAt) { this.dropoffAt = dropoffAt; }

    Long getDropoffMileageKm() { return dropoffMileageKm; }
    void setDropoffMileageKm(Long dropoffMileageKm) { this.dropoffMileageKm = dropoffMileageKm; }

    Short getDropoffFuelPercent() { return dropoffFuelPercent; }
    void setDropoffFuelPercent(Short dropoffFuelPercent) { this.dropoffFuelPercent = dropoffFuelPercent; }

    Long getVersion() { return version; }
}
