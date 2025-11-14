package com.botanybay;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a suspect that has been banned and is awaiting a Botany Bay trial.
 */
final class QueuedSuspect {

    private final UUID suspectId;
    private final String suspectName;
    private final String accusation;
    private final Instant queuedAt;

    QueuedSuspect(final UUID suspectId, final String suspectName, final String accusation, final Instant queuedAt) {
        this.suspectId = Objects.requireNonNull(suspectId, "suspectId");
        this.suspectName = Objects.requireNonNull(suspectName, "suspectName");
        this.accusation = Objects.requireNonNull(accusation, "accusation");
        this.queuedAt = Objects.requireNonNull(queuedAt, "queuedAt");
    }

    UUID getSuspectId() {
        return suspectId;
    }

    String getSuspectName() {
        return suspectName;
    }

    String getAccusation() {
        return accusation;
    }

    Instant getQueuedAt() {
        return queuedAt;
    }
}
