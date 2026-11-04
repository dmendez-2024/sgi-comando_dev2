package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso del monitorista (p. ej. CAJ-OBS-001): una pasada por las consignas OBSINT activas, una tarea por consigna.
 * Toda acción sobre una tarea pasa por aquí para hacer cumplir el orden (no se inicia la tarea 2 sin responder la 1).
 */
@Entity
@Table(name = "obsint_case")
public class ObsintCase extends BaseEntity {
    public enum Status { IN_PROGRESS, COMPLETED }

    @Column(nullable = false, length = 40) public String code;
    @Column(nullable = false, length = 160) public String operator;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Status status = Status.IN_PROGRESS;
    @Column(name = "started_at", nullable = false) public Instant startedAt;
    @Column(name = "completed_at") public Instant completedAt;

    @OneToMany(mappedBy = "obsintCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    public List<ObsintCaseTask> tasks = new ArrayList<>();

    public static ObsintCase open(UUID instanceCountryId, String code, String operator, List<ObsintConsigna> consignas, Instant now) {
        if (consignas.isEmpty()) throw new ObsintRuleException("No hay consignas OBSINT activas para evaluar.");
        ObsintCase c = new ObsintCase();
        c.instanceCountryId = instanceCountryId;
        c.code = code;
        c.operator = operator;
        c.startedAt = now;
        for (int i = 0; i < consignas.size(); i++) c.tasks.add(ObsintCaseTask.from(c, consignas.get(i), i + 1));
        return c;
    }

    /** La primera tarea sin responder; vacío cuando el caso terminó. */
    public Optional<ObsintCaseTask> currentTask() {
        return tasks.stream().filter(t -> t.status != ObsintCaseTask.Status.ANSWERED).findFirst();
    }

    public ObsintCaseTask task(UUID taskId) {
        return tasks.stream().filter(t -> taskId.equals(t.id)).findFirst().orElseThrow(() -> new NotFoundException("Tarea no encontrada"));
    }

    public ObsintCaseTask startTask(ObsintCaseTask task, Instant now) {
        requireCurrent(task);
        task.start(now);
        return task;
    }

    public void answerTask(ObsintCaseTask task, ObsintCaseTask.Decision decision, String justification, Instant now) {
        requireCurrent(task);
        task.answer(decision, justification, now);
        if (currentTask().isEmpty()) {
            status = Status.COMPLETED;
            completedAt = now;
        }
    }

    private void requireCurrent(ObsintCaseTask task) {
        if (status == Status.COMPLETED) throw new ObsintRuleException("El caso ya terminó.");
        ObsintCaseTask current = currentTask().orElseThrow();
        if (current != task) throw new ObsintRuleException("Las tareas se resuelven en orden; la actual es la " + current.position + ".");
    }
}
