package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

/**
 * Una tarea del caso. Copia la consigna (código, instrucción y tiempo máximo) y guarda la respuesta del monitorista.
 * Las reglas viven aquí para poder probarlas sin base de datos.
 */
@Entity
@Table(name = "obsint_case_task")
public class ObsintCaseTask extends BaseEntity {
    public enum Status { PENDING, IN_PROGRESS, ANSWERED }
    public enum Decision { YES, NO, UNCERTAIN }
    public static final int JUSTIFICATION_MAX = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "case_id") public ObsintCase obsintCase;
    /** Origen de la tarea; de aquí salen las cámaras. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "consigna_id") public ObsintConsigna consigna;
    @Column(nullable = false) public int position;
    @Column(name = "consigna_code", nullable = false, length = 40) public String consignaCode;
    @Column(nullable = false, length = 1000) public String instruction;
    @Column(name = "time_limit_seconds", nullable = false) public int timeLimitSeconds;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Status status = Status.PENDING;
    @Column(name = "started_at") public Instant startedAt;
    @Column(name = "answered_at") public Instant answeredAt;
    @Enumerated(EnumType.STRING) @Column(length = 20) public Decision decision;
    @Column(length = JUSTIFICATION_MAX) public String justification;
    @Column(name = "seconds_used") public Integer secondsUsed;
    /** true si se respondió después de vencido el tiempo. Responder tarde se permite, pero queda registrado. */
    @Column(name = "timed_out") public Boolean timedOut;

    static ObsintCaseTask from(ObsintCase c, ObsintConsigna source, int position) {
        ObsintCaseTask t = new ObsintCaseTask();
        t.instanceCountryId = c.instanceCountryId;
        t.obsintCase = c;
        t.consigna = source;
        t.position = position;
        t.consignaCode = source.code;
        t.instruction = source.instruction;
        t.timeLimitSeconds = source.timeLimitSeconds;
        return t;
    }

    /** Inicia el tiempo. Iniciar una tarea ya en curso no reinicia el reloj (la web puede recargar sin perder tiempo). */
    void start(Instant now) {
        switch (status) {
            case PENDING -> { status = Status.IN_PROGRESS; startedAt = now; }
            case IN_PROGRESS -> { }
            case ANSWERED -> throw new ObsintRuleException("La tarea ya fue respondida.");
        }
    }

    void answer(Decision decision, String justification, Instant now) {
        requireInProgress();
        if (decision == null) throw new ObsintRuleException("Selecciona Sí, No o Incierto.");
        String text = justification == null ? "" : justification.trim();
        if (text.isEmpty()) throw new ObsintRuleException("Ingresa una justificación antes de continuar.");
        if (text.length() > JUSTIFICATION_MAX) throw new ObsintRuleException("La justificación admite hasta " + JUSTIFICATION_MAX + " caracteres.");
        this.decision = decision;
        this.justification = text;
        this.answeredAt = now;
        this.secondsUsed = (int) Duration.between(startedAt, now).toSeconds();
        this.timedOut = now.isAfter(deadline());
        this.status = Status.ANSWERED;
    }

    public void requireInProgress() {
        if (status != Status.IN_PROGRESS) throw new ObsintRuleException("La tarea no está en curso.");
    }

    public Instant deadline() {
        return startedAt == null ? null : startedAt.plusSeconds(timeLimitSeconds);
    }
}
