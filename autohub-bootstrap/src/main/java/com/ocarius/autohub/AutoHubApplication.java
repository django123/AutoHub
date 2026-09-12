package com.ocarius.autohub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree de l'application.
 *
 * <h2>Pourquoi cette classe est presque vide</h2>
 * C'est volontaire, et c'est un bon signe. Le module {@code bootstrap} ne fait
 * qu'<b>assembler</b> : il ne contient ni regle metier, ni cas d'usage, ni
 * adaptateur. Si du code metier finit par atterrir ici, c'est le symptome d'un
 * probleme de decoupage.
 *
 * <h2>Pourquoi le scan de composants fonctionne</h2>
 * {@code @SpringBootApplication} scanne le package de cette classe et ses
 * sous-packages. Comme elle est dans {@code com.ocarius.autohub} et que tous
 * les modules partagent cette racine, les {@code @Component} de
 * {@code autohub-infrastructure} sont decouverts automatiquement.
 *
 * <p>Et le domaine, alors ? Il n'a aucune annotation, donc rien a scanner.
 * Il est utilise comme une bibliotheque ordinaire -- exactement comme on
 * utiliserait Guava. C'est le resultat recherche.
 */
@SpringBootApplication
public class AutoHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoHubApplication.class, args);
    }
}
