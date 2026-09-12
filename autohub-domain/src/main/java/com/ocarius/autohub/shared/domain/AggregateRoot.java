package com.ocarius.autohub.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Classe de base des racines d'agregats.
 *
 * <h2>Qu'est-ce qu'un agregat ?</h2>
 * Un groupe d'objets qu'on charge, modifie et sauvegarde <b>ensemble</b>,
 * parce qu'ils partagent des invariants. La racine est le seul point d'entree :
 * l'exterieur ne manipule jamais les objets internes directement.
 *
 * <p>Regle de dimensionnement : un agregat = une transaction = un verrou.
 * Si tu hesites, fais-le plus petit. Les gros agregats provoquent des
 * contentions en base et des chargements inutiles.
 *
 * <h2>Deux points d'attention sur cette classe</h2>
 *
 * <b>1. Egalite par identite, pas par valeur.</b> Deux {@code Vehicle} avec
 * le meme {@code id} sont la <i>meme</i> voiture, meme si l'un a 10 000 km de
 * plus parce qu'il a ete charge il y a une heure. C'est la difference
 * fondamentale avec un Value Object, ou l'egalite porte sur le contenu.
 *
 * <b>2. Les evenements s'accumulent, ils ne se publient pas.</b> L'agregat
 * n'a aucun moyen de publier quoi que ce soit : il n'a pas de dependance vers
 * l'exterieur, et c'est voulu. Il enregistre ses evenements, la couche
 * application les recolte apres commit via {@link #pullDomainEvents()}.
 *
 * <p>Publier depuis l'agregat serait une faute : l'evenement partirait avant
 * que la transaction ne soit validee, et un rollback laisserait des
 * consommateurs informes d'un fait qui n'a jamais eu lieu.
 *
 * @param <ID> le type d'identifiant, garantissant qu'un {@code VehicleId}
 *             ne peut pas servir a identifier un {@code Rental}
 */
public abstract class AggregateRoot<ID extends EntityId> {

    private final ID id;

    /**
     * Evenements produits depuis le chargement de l'agregat.
     * {@code transient} par intention : cet etat n'est jamais persiste.
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(ID id) {
        this.id = Objects.requireNonNull(id, "L'identifiant d'un agregat ne peut pas etre null");
    }

    public ID id() {
        return id;
    }

    /**
     * Enregistre un evenement. {@code protected} : seul l'agregat lui-meme
     * decide de ce qui merite d'etre annonce au reste du systeme.
     */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event));
    }

    /**
     * Recupere les evenements <b>et vide la liste</b>.
     *
     * <p>Le vidage est essentiel : sans lui, un agregat sauvegarde deux fois
     * dans la meme transaction republierait ses evenements. Le nom
     * {@code pull} (et non {@code get}) signale cet effet de bord.
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> collected = List.copyOf(domainEvents);
        domainEvents.clear();
        return collected;
    }

    /** Lecture seule, sans effet de bord. Utile en test. */
    public List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        // getClass() et non instanceof : un proxy Hibernate ou une sous-classe
        // ne doivent pas etre consideres egaux a l'entite d'origine.
        if (other == null || getClass() != other.getClass()) return false;
        return id.equals(((AggregateRoot<?>) other).id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id.value() + "]";
    }
}
