package com.ocarius.autohub.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
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
    // depanner" dans un controleur ou un service. Depuis le WE 5, RentalJpaEntity
    // existe : la regle n'est plus vide, allowEmptyShould a donc ete retire --
    // si toutes les entites disparaissaient un jour, on veut le savoir.
    @ArchTest
    static final ArchRule les_entites_jpa_restent_dans_la_persistance = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..infrastructure..persistence..")
            .because("une entite JPA est un detail de stockage, pas un objet metier");

    // Complement ajoute au week-end 5, quand la premiere entite JPA est apparue.
    // La regle 3 dit OU vit une entite ; celle-ci dit QUI peut la voir. Sans
    // elle, un futur controleur pourrait renvoyer directement une
    // RentalJpaEntity en JSON -- le mapper serait contourne, et le schema de
    // la base deviendrait de fait le contrat de l'API.
    @ArchTest
    static final ArchRule seule_la_persistance_manipule_les_entites_jpa = noClasses()
            .that().resideOutsideOfPackage("..infrastructure..persistence..")
            .should().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .because("le reste de l'application ne parle qu'aux agregats, via le mapper");

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

    // Ajoutees au WE7, AVANT d'ecrire la mise a jour du kilometrage au retour
    // (ADR 0004) : la tentation d'appeler VehicleRepository depuis rental
    // arrive exactement a ce moment-la. La barriere est posee avant.
    @ArchTest
    static final ArchRule rental_ne_depend_pas_de_fleet = noClasses()
            .that().resideInAPackage("..rental..")
            .should().dependOnClassesThat().resideInAPackage("..fleet..")
            .because("rental ne connait d'un vehicule que son VehicleId (shared kernel) et sa vue "
                    + "VehicleCatalog ; le kilometrage voyage par evenement (ADR 0004)");

    // Le seul passage autorise de fleet vers rental : les evenements publies par
    // rental, qui sont un contrat. En microservices, ce serait le schema d'un
    // message ; ici, une classe du paquet rental.domain.event.
    @ArchTest
    static final ArchRule fleet_ne_connait_de_rental_que_ses_evenements = noClasses()
            .that().resideInAPackage("..fleet..")
            .should().dependOnClassesThat(
                    resideInAPackage("..rental..").and(not(resideInAPackage("..rental.domain.event.."))))
            .because("fleet reagit aux evenements de rental, il n'appelle jamais son code (ADR 0004)");

    // =================================================================
    // REGLE 4 bis : les controleurs ne voient que des ports
    // =================================================================
    // Ajoutee au WE6 avec la couche REST. Un controleur qui importe le domaine
    // finit par construire un agregat lui-meme, ou par le renvoyer en JSON ; un
    // controleur qui importe un service contourne l'interface et se lie a une
    // implementation.
    @ArchTest
    static final ArchRule les_controleurs_ne_connaissent_que_les_ports = noClasses()
            .that().resideInAPackage("..infrastructure.in.rest..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..application.service..")
            .because("un controleur traduit HTTP en appel de port entrant, rien de plus");

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
