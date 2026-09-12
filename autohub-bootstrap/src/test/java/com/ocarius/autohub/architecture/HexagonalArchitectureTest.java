package com.ocarius.autohub.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * =====================================================================
 * LE TEST LE PLUS RENTABLE DU PROJET
 * =====================================================================
 *
 * <p>Ecrit au week-end 1, avant qu'il n'y ait quoi que ce soit a proteger.
 * Ce n'est pas un exces de zele : une regle architecturale ajoutee apres coup
 * echoue sur cinquante violations existantes, et finit invariablement
 * commentee avec un {@code // TODO a reactiver}.
 *
 * <p>Ces tests transforment "je fais attention a ne pas melanger les couches"
 * en "la CI est rouge si je le fais". La difference est enorme : la discipline
 * personnelle s'erode un vendredi soir a 19h, une build cassee non.
 *
 * <p>Le decoupage Maven en modules offre deja une premiere barriere (le
 * compilateur refuse une dependance inverse). ArchUnit couvre ce que Maven ne
 * voit pas : les frontieres <i>a l'interieur</i> d'un module, et l'isolation
 * entre contextes metier.
 */
@AnalyzeClasses(
        packages = "com.ocarius.autohub",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    // =================================================================
    // REGLE 1 : le domaine ignore l'existence des frameworks
    // =================================================================
    // C'est la regle cardinale. Une seule annotation @Entity dans le domaine
    // et tout l'edifice s'ecroule : le modele metier se met a epouser les
    // contraintes de l'ORM (constructeur vide, setters, collections mutables)
    // au lieu d'exprimer les regles metier.
    @ArchTest
    static final ArchRule le_domaine_ne_depend_d_aucun_framework = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.validation..",
                    "com.fasterxml.jackson..",
                    "org.hibernate..")
            .because("le domaine doit rester du Java pur, testable en millisecondes "
                    + "et reutilisable hors de Spring");

    // =================================================================
    // REGLE 2 : sens des dependances (le coeur de l'hexagone)
    // =================================================================
    @ArchTest
    static final ArchRule le_domaine_ignore_l_application_et_l_infrastructure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..")
            .because("les dependances pointent toujours VERS le domaine, jamais depuis lui");

    @ArchTest
    static final ArchRule l_application_ignore_l_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("l'application declare des ports ; elle ne connait pas leurs adaptateurs");

    // =================================================================
    // REGLE 3 : JPA reste confine dans la persistance
    // =================================================================
    // Sans cette regle, une @Entity finit toujours par apparaitre "juste pour
    // depanner" dans un controleur ou un service. allowEmptyShould(true) est
    // necessaire tant qu'aucune entite JPA n'existe (elles arrivent au WE 5).
    @ArchTest
    static final ArchRule les_entites_jpa_restent_dans_la_persistance = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..infrastructure..persistence..")
            .allowEmptyShould(true)
            .because("une entite JPA est un detail de stockage, pas un objet metier");

    // =================================================================
    // REGLE 4 : isolation des contextes metier
    // =================================================================
    // Les modules fleet / rental / sales n'existent pas encore : ces regles
    // passent a vide aujourd'hui et deviendront actives au fil des phases.
    // C'est exactement le but -- la barriere est posee AVANT la tentation.
    @ArchTest
    static final ArchRule rental_ne_depend_pas_de_sales = noClasses()
            .that().resideInAPackage("..rental..")
            .should().dependOnClassesThat().resideInAPackage("..sales..")
            .allowEmptyShould(true)
            .because("les contextes communiquent par evenements, jamais par appel direct");

    @ArchTest
    static final ArchRule sales_ne_depend_pas_de_rental = noClasses()
            .that().resideInAPackage("..sales..")
            .should().dependOnClassesThat().resideInAPackage("..rental..")
            .allowEmptyShould(true)
            .because("c'est ce qui rendra la greffe de la vente indolore au WE 10-11");

    // =================================================================
    // REGLE 5 : vue d'ensemble en couches
    // =================================================================
    // Redondante avec les regles 2 et 3, mais le message d'erreur produit est
    // bien plus lisible : ArchUnit dessine la violation en termes de couches.
    @ArchTest
    static final ArchRule architecture_en_couches = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")

            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    // =================================================================
    // REGLE 6 : hygiene de base
    // =================================================================
    @ArchTest
    static final ArchRule pas_de_system_out = noClasses()
            .should().callMethod(System.class, "currentTimeMillis")
            .because("utiliser java.time.Clock, injectable et donc testable, "
                    + "plutot que l'horloge systeme figee dans le code");
}
