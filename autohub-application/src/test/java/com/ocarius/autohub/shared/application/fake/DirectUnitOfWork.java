package com.ocarius.autohub.shared.application.fake;

import com.ocarius.autohub.shared.application.port.out.UnitOfWork;

import java.util.function.Supplier;

/**
 * Fake de {@link UnitOfWork} : execute le travail tel quel, sans transaction.
 * Compte les transactions ouvertes, pour verifier la decoupe d'un cas d'usage.
 */
public final class DirectUnitOfWork implements UnitOfWork {

    private int transactions;

    @Override
    public <T> T execute(Supplier<T> work) {
        transactions++;
        return work.get();
    }

    /** Nombre de transactions ouvertes depuis la creation. */
    public int transactions() {
        return transactions;
    }
}
