package com.ocarius.autohub.shared.infrastructure.error;

import com.ocarius.autohub.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * ADAPTATEUR ENTRANT : traduit les exceptions du domaine en reponses HTTP.
 *
 * <h2>Pourquoi cette classe est ici et pas dans le domaine</h2>
 * Le domaine ignore HTTP. Il leve {@code DomainException} avec un code metier ;
 * c'est <b>l'adaptateur</b> qui decide que cela vaut un 409 ou un 422. Si demain
 * on expose le meme cas d'usage via gRPC ou un consumer Kafka, un autre
 * adaptateur fera une autre traduction, sans que le metier ne bouge.
 *
 * <h2>Format de reponse : RFC 9457 (ex-7807)</h2>
 * {@link ProblemDetail} est le support natif de Spring pour ce standard.
 * Plutot qu'un JSON maison, on renvoie un format que les clients savent deja
 * interpreter :
 * <pre>{@code
 * {
 *   "type": "https://autohub.ocarius.com/errors/DATE_RANGE_INVALID",
 *   "title": "Regle metier violee",
 *   "status": 422,
 *   "detail": "La fin doit etre strictement posterieure au debut",
 *   "code": "DATE_RANGE_INVALID",
 *   "timestamp": "2026-09-12T10:15:30Z"
 * }
 * }</pre>
 * Le champ {@code code} est ce sur quoi un client programmatique doit
 * brancher sa logique -- jamais sur {@code detail}, qui est du texte humain
 * susceptible d'etre reformule ou traduit.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://autohub.ocarius.com/errors/";

    /**
     * 422 UNPROCESSABLE ENTITY : la requete est bien formee, mais le metier la refuse.
     *
     * <p>Ne pas confondre avec 400 BAD REQUEST, qui signale une requete
     * malformee (JSON invalide, champ manquant). Ici la requete est
     * comprehensible ; c'est une regle du domaine qui s'y oppose.
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException exception) {
        // Niveau WARN, pas ERROR : une regle metier qui joue son role n'est pas
        // un incident. Si ce log passe en ERROR, tes alertes crieront au loup.
        log.warn("Regle metier violee [{}] : {}", exception.code(), exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Regle metier violee");
        problem.setType(URI.create(ERROR_BASE_URI + exception.code()));
        problem.setProperty("code", exception.code());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * 500 : tout le reste. Filet de securite.
     *
     * <p>Point de securite important : on logge la stacktrace cote serveur mais
     * on ne renvoie <b>jamais</b> {@code exception.getMessage()} au client.
     * Un message d'erreur technique fuit volontiers des noms de tables, des
     * chemins de fichiers ou des versions de composants -- autant de cadeaux
     * pour un attaquant.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Erreur inattendue", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue.");
        problem.setTitle("Erreur interne");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
