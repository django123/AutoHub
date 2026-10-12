package com.ocarius.autohub.fleet.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representation JPA d'une ligne de la table {@code vehicle}.
 *
 * <p>Meme parti pris que {@code RentalJpaEntity} : un sac de colonnes, sans
 * logique, avec des accesseurs de paquet. Les enums du domaine sont stockes en
 * {@code String}, les offres en deux booleens ; la conversion est dans
 * {@link VehicleMapper}.
 */
@Entity
@Table(name = "vehicle")
public class VehicleJpaEntity {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "vin", nullable = false, length = 17)
    private String vin;

    @Column(name = "plate_number", nullable = false, length = 9)
    private String plateNumber;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "model", nullable = false)
    private String model;

    /** {@code Short} : la colonne est un smallint, et la validation du schema compare les types. */
    @Column(name = "model_year", nullable = false)
    private Short modelYear;

    @Column(name = "mileage_km", nullable = false)
    private Long mileageKm;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "condition", nullable = false)
    private String condition;

    @Column(name = "for_rent", nullable = false)
    private Boolean forRent;

    @Column(name = "for_sale", nullable = false)
    private Boolean forSale;

    @Column(name = "daily_rate_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRateAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "daily_rate_currency", nullable = false, length = 3)
    private String dailyRateCurrency;

    /** Verrouillage optimiste, gere par Hibernate uniquement (voir RentalJpaEntity). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Exige par JPA. */
    protected VehicleJpaEntity() {
    }

    /** Entite vide pour un vehicule qui n'existe pas encore en base. */
    VehicleJpaEntity(UUID id) {
        this.id = id;
    }

    UUID getId() { return id; }

    String getVin() { return vin; }
    void setVin(String vin) { this.vin = vin; }

    String getPlateNumber() { return plateNumber; }
    void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    String getBrand() { return brand; }
    void setBrand(String brand) { this.brand = brand; }

    String getModel() { return model; }
    void setModel(String model) { this.model = model; }

    Short getModelYear() { return modelYear; }
    void setModelYear(Short modelYear) { this.modelYear = modelYear; }

    Long getMileageKm() { return mileageKm; }
    void setMileageKm(Long mileageKm) { this.mileageKm = mileageKm; }

    String getStatus() { return status; }
    void setStatus(String status) { this.status = status; }

    String getCondition() { return condition; }
    void setCondition(String condition) { this.condition = condition; }

    Boolean getForRent() { return forRent; }
    void setForRent(Boolean forRent) { this.forRent = forRent; }

    Boolean getForSale() { return forSale; }
    void setForSale(Boolean forSale) { this.forSale = forSale; }

    BigDecimal getDailyRateAmount() { return dailyRateAmount; }
    void setDailyRateAmount(BigDecimal dailyRateAmount) { this.dailyRateAmount = dailyRateAmount; }

    String getDailyRateCurrency() { return dailyRateCurrency; }
    void setDailyRateCurrency(String dailyRateCurrency) { this.dailyRateCurrency = dailyRateCurrency; }
}
