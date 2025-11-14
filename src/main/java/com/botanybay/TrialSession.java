package com.botanybay;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks a single Botany Bay trial and the votes cast during it.
 */
final class TrialSession {

    private final UUID suspectId;
    private final String suspectName;
    private final String accusation;
    private final Instant startedAt;
    private final Map<UUID, PunishmentOption> votes = new HashMap<>();

    TrialSession(final UUID suspectId, final String suspectName, final String accusation) {
        this.suspectId = suspectId;
        this.suspectName = suspectName;
        this.accusation = accusation;
        this.startedAt = Instant.now();
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

    Instant getStartedAt() {
        return startedAt;
    }

    Duration elapsed() {
        return Duration.between(startedAt, Instant.now());
    }

    void castVote(final UUID voter, final PunishmentOption option) {
        votes.put(voter, option);
    }

    boolean hasVoted(final UUID voter) {
        return votes.containsKey(voter);
    }

    Map<PunishmentOption, Integer> getVoteCounts() {
        final Map<PunishmentOption, Integer> counts = new EnumMap<>(PunishmentOption.class);
        for (final PunishmentOption option : PunishmentOption.values()) {
            counts.put(option, 0);
        }
        for (final PunishmentOption vote : votes.values()) {
            counts.computeIfPresent(vote, (key, value) -> value + 1);
        }
        return counts;
    }
}
