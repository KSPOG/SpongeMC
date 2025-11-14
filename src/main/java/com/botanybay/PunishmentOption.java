package com.botanybay;

import java.util.Locale;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;


/**
 * Represents the punishments available during a Botany Bay trial.
 */
public enum PunishmentOption {

    EXECUTE("execute", "Execute the botter in front of the gathered crowd."),
    PILLORY("pillory", "Send the botter to the pillory for public humiliation."),
    RELEASE("release", "Grant mercy and release the accused player.");

    private final String id;
    private final String description;

    PunishmentOption(final String id, final String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {

        return this.id;
    }

    public String getDescription() {
        return this.description;
    }

    public Component displayName() {
        switch (this) {
            case EXECUTE:
                return Component.text("Execution", NamedTextColor.DARK_RED);
            case PILLORY:
                return Component.text("Pillory", NamedTextColor.GOLD);
            default:
                return Component.text("Release", NamedTextColor.GREEN);


        return id;
    }

    public String getDescription() {
        return description;
    }

    public Text toText() {
        switch (this) {
            case EXECUTE:
                return Text.of(TextColors.DARK_RED, "Execution");
            case PILLORY:
                return Text.of(TextColors.GOLD, "Pillory");
            default:
                return Text.of(TextColors.GREEN, "Release");


        }
    }

    public static Optional<PunishmentOption> fromInput(final String input) {
        if (input == null || input.trim().isEmpty()) {
            return Optional.empty();
        }

        final String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (final PunishmentOption option : values()) {
            if (option.id.equals(normalized) || option.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(option);
            }
        }

        return Optional.empty();
    }
}
