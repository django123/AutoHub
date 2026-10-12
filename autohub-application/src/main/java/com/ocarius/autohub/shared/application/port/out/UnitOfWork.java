package com.ocarius.autohub.shared.application.port.out;

import java.util.function.Supplier;

/**
 * PORT SORTANT : delimite une transaction.
 *
 * <h2>Pourquoi un port, et pas {@code @Transactional}</h2>
 * La frontiere transactionnelle est une decision de <b>cas d'usage</b> : c'est
 * lui qui sait que "expirer la reservation perimee" et "enregistrer la
 * nouvelle" doivent reussir ou echouer ensemble. Elle ne doit donc vivre ni
 * dans le controleur (une transaction ouverte pendant la serialisation JSON,
 * et un cas d'usage qui ne serait plus transactionnel s'il etait appele par un
 * job ou un listener), ni dans l'adaptateur de repository (deux
 * {@code save} d'un meme cas d'usage seraient deux transactions).
 *
 * <p>Mais la couche application n'a pas Spring sur son classpath, et ne doit
 * pas l'avoir : un cas d'usage s'instancie avec un {@code new} dans un test.
 * {@code @Transactional} y est donc impossible. Ce port exprime la frontiere
 * dans le code du cas d'usage, et l'infrastructure fournit le mecanisme
 * ({@code SpringUnitOfWork}, un {@code TransactionTemplate}). En test, un fake
 * de trois lignes execute simplement le travail.
 *
 * <h2>Pourquoi pas dans le domaine</h2>
 * Un agregat est une frontiere de coherence, pas de transaction : il ne sait
 * pas s'il est sauvegarde seul ou avec d'autres, ni meme s'il est sauvegarde.
 *
 * <p>Voir ADR 0005.
 */
public interface UnitOfWork {

    /**
     * Execute {@code work} dans une transaction, et renvoie son resultat. Une
     * exception levee par {@code work} annule toute la transaction.
     */
    <T> T execute(Supplier<T> work);

    /**
     * Variante sans resultat. Nom distinct de {@link #execute} plutot qu'une
     * surcharge : une lambda {@code () -> repository.findById(id)} serait
     * compatible a la fois avec {@code Supplier} et {@code Runnable}, et le
     * compilateur refuserait de choisir.
     */
    default void run(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
