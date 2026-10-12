package com.ocarius.autohub.rental.domain.pricing;

import com.ocarius.autohub.shared.domain.vo.Money;

import java.util.Optional;

/**
 * Une regle de tarification, composable avec les autres.
 *
 * <h2>Pourquoi la politique recoit le sous-total courant</h2>
 * Une remise de 20 % s'applique "a ce qui precede" : elle a besoin du montant
 * accumule jusque-la. L'alternative, recevoir le tarif de base et la liste des
 * ajustements deja appliques, est plus expressive (une politique pourrait
 * ignorer une majoration precise), mais personne n'en a besoin aujourd'hui.
 * On garde la forme la plus simple qui compose.
 *
 * <h2>La consequence : l'ordre compte</h2>
 * Des qu'une politique depend du sous-total, la composition n'est plus
 * commutative. Remise de 20 % puis majoration de 15 EUR sur 3 000 EUR :
 * 2 415 EUR. Majoration puis remise : 2 412 EUR. L'ordre doit donc etre une
 * donnee explicite du domaine : c'est le role de {@link PricingService}.
 *
 * <p>Une politique est sans etat et ne lit jamais l'horloge : meme entree,
 * meme ligne.
 */
public interface PricingPolicy {

    /**
     * Calcule la ligne que cette politique ajoute a la facture.
     *
     * @param context  tarif journalier et periode
     * @param subtotal somme des lignes produites par les politiques precedentes
     * @return la ligne a ajouter, ou vide si la politique ne s'applique pas
     */
    Optional<PriceLine> apply(PricingContext context, Money subtotal);
}
