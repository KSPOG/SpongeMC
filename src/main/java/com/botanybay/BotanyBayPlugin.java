package com.botanybay;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;


import java.util.ArrayDeque;
import java.util.ArrayList;
import com.flowpowered.math.vector.Vector3i;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.entity.Sign;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.Humanoid;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.ban.Ban;
import org.spongepowered.api.service.ban.BanTypes;
import org.spongepowered.api.util.ban.Ban;
import org.spongepowered.api.util.ban.BanTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

@Plugin("botanybay")
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.Human;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.ban.BanService;

import org.spongepowered.api.util.ban.Ban;
import org.spongepowered.api.util.ban.BanTypes;
import org.spongepowered.api.service.ban.Ban;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.service.ban.BanTypes;

import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

/**
 * Sponge plugin that recreates the public Botany Bay trials from RuneScape.
 *
 * <p>An administrator can summon a trial for a suspected botter. Players can vote on
 * the punishment that should be delivered. After the countdown expires the
 * punishment with the most votes is announced to the server. This provides a
 * social storytelling tool reminiscent of the original Botany Bay event.</p>
 */
@Plugin(
        id = "botanybay",
        name = "Botany Bay",
        version = "1.0.0",
        description = "Facilitates Botany Bay style public trials for accused botters."
)

public final class BotanyBayPlugin {

    private static final Duration TRIAL_DURATION = Duration.ofMinutes(2);
    private static final String DEFAULT_ACCUSATION = "Botting-related offences";
    private static final int SIGN_LINE_LENGTH = 15;

    private final Logger logger;
    private final PluginContainer pluginContainer;

    private final Deque<QueuedSuspect> trialQueue = new ArrayDeque<>();
    private final Set<UUID> queuedSuspects = new HashSet<>();
    private final Map<UUID, ZoneSelection> pendingZoneSelections = new HashMap<>();
    private final Set<UUID> pendingBanSignSelections = new HashSet<>();
    private Optional<ServerLocation> npcSpawnLocation = Optional.empty();
    private Optional<ServerLocation> banSignLocation = Optional.empty();
    private Optional<VoteZone> voteZone = Optional.empty();
    private TrialSession activeTrial;
    private ScheduledTask conclusionTask;
    private UUID npcEntityId;

    private final Parameter.Value<User> suspectParameter = Parameter.user().key("suspect").build();
    private final Parameter.Value<String> reasonParameter = Parameter.remainingJoinedStrings().key("reason").build();
    private final Parameter.Value<String> punishmentParameter = Parameter.string().key("punishment").build();

    @Inject
    public BotanyBayPlugin(final Logger logger, final PluginContainer pluginContainer) {
        this.logger = logger;
        this.pluginContainer = pluginContainer;
    }

    @Listener
    public void onServerStarted(final StartedEngineEvent<Server> event) {
        this.logger.info("Botany Bay plugin ready. Use /botanybay start <player> to begin a trial.");
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        event.register(this.pluginContainer, this.createRootCommand(), "botanybay", "bbay");
    }

    @Listener
    public void onAccusedDisconnect(final ServerSideConnectionEvent.Disconnect event) {
        if (this.activeTrial != null && this.activeTrial.getSuspectId().equals(event.player().uniqueId())) {
            Sponge.server().broadcastAudience().sendMessage(Component.text()
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("The accused has fled Botany Bay! The trial ends without a verdict.")
                            .color(NamedTextColor.GRAY))
                    .build());
            this.cancelScheduledConclusion();
            this.activeTrial = null;
        }
        final UUID playerId = event.player().uniqueId();
        this.pendingZoneSelections.remove(playerId);
        this.pendingBanSignSelections.remove(playerId);
    }

    private Command.Parameterized createRootCommand() {
        final Command.Parameterized startCommand = Command.builder()
                .permission("botanybay.command.start")
                .addParameter(Parameter.optional(this.suspectParameter))
                .addParameter(Parameter.optional(this.reasonParameter))
                .executor(this::executeStart)
                .build();

        final Command.Parameterized banCommand = Command.builder()
                .permission("botanybay.command.ban")
                .addParameter(this.suspectParameter)
                .addParameter(Parameter.optional(this.reasonParameter))
                .executor(this::executeBan)
                .build();

        final Command.Parameterized voteCommand = Command.builder()
                .permission("botanybay.command.vote")
                .addParameter(this.punishmentParameter)
                .executor(this::executeVote)
                .build();

        final Command.Parameterized statusCommand = Command.builder()
                .permission("botanybay.command.status")
                .executor(this::executeStatus)
                .build();

        final Command.Parameterized cancelCommand = Command.builder()
                .permission("botanybay.command.cancel")
                .executor(this::executeCancel)
                .build();

        final Command.Parameterized setNpcCommand = Command.builder()
                .permission("botanybay.command.setnpc")
                .executor(this::executeSetNpc)
                .build();

        final Command.Parameterized setZoneCommand = Command.builder()
                .permission("botanybay.command.setzone")
                .executor(this::executeSetZone)
                .build();

        final Command.Parameterized setBanSignCommand = Command.builder()
                .permission("botanybay.command.setbansign")
                .executor(this::executeSetBanSign)
                .build();

        final Command.Parameterized setCommand = Command.builder()
                .permission("botanybay.command.set")
                .child(setNpcCommand, "npc")
                .child(setZoneCommand, "zone")
                .child(setBanSignCommand, "bansign", "sign")
                .executor(context -> {
                    context.cause().sendMessage(Component.text()
                            .color(NamedTextColor.YELLOW)
                            .append(Component.text("Usage: /botanybay set <npc|zone|bansign>"))
                            .build());
                
    private TrialSession activeTrial;
    private Task conclusionTask;
    private final Deque<QueuedSuspect> trialQueue = new ArrayDeque<>();
    private final Set<UUID> queuedSuspects = new HashSet<>();
    private Optional<Location<World>> npcSpawnLocation = Optional.empty();
    private Optional<Location<World>> banSignLocation = Optional.empty();
    private UUID npcEntityId;
    private final Map<UUID, ZoneSelection> pendingZoneSelections = new HashMap<>();
    private final Set<UUID> pendingBanSignSelections = new HashSet<>();
    private Optional<VoteZone> voteZone = Optional.empty();

    @Inject
    public BotanyBayPlugin(final Logger logger) {
        this.logger = logger;
    }

    @Listener
    public void onGameInitialization(final GameInitializationEvent event) {
        Sponge.getCommandManager().register(this, createRootCommand(), "botanybay", "bbay");
        logger.info("Botany Bay plugin ready. Use /botanybay start <player> to begin a trial.");
    }

    @Listener
    public void onAccusedDisconnect(final ClientConnectionEvent.Disconnect event) {
        if (activeTrial != null && activeTrial.getSuspectId().equals(event.getTargetEntity().getUniqueId())) {
            Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GRAY,
                    "The accused has fled Botany Bay! The trial ends without a verdict."));
            cancelScheduledConclusion();
            activeTrial = null;
        }
        final UUID playerId = event.getTargetEntity().getUniqueId();
        pendingZoneSelections.remove(playerId);
        pendingBanSignSelections.remove(playerId);
    }

    private CommandSpec createRootCommand() {
        final CommandSpec startCommand = CommandSpec.builder()
                .description(Text.of("Begin a Botany Bay trial against a suspect or the next in the queue."))
                .permission("botanybay.command.start")
                .arguments(
                        GenericArguments.optional(GenericArguments.user(Text.of("suspect"))),
                        GenericArguments.optional(GenericArguments.remainingJoinedStrings(Text.of("reason")))
                )
                .executor(startTrialExecutor())
                .build();

        final CommandSpec banCommand = CommandSpec.builder()
                .description(Text.of("Ban a suspect and add them to the Botany Bay trial queue."))
                .permission("botanybay.command.ban")
                .arguments(
                        GenericArguments.user(Text.of("suspect")),
                        GenericArguments.optional(GenericArguments.remainingJoinedStrings(Text.of("reason")))
                )
                .executor(banExecutor())
                .build();

        final CommandSpec voteCommand = CommandSpec.builder()
                .description(Text.of("Cast your vote in the ongoing Botany Bay trial."))
                .permission("botanybay.command.vote")
                .arguments(GenericArguments.string(Text.of("punishment")))
                .executor(voteExecutor())
                .build();

        final CommandSpec statusCommand = CommandSpec.builder()
                .description(Text.of("View the status of the current Botany Bay trial."))
                .permission("botanybay.command.status")
                .executor(statusExecutor())
                .build();

        final CommandSpec cancelCommand = CommandSpec.builder()
                .description(Text.of("Cancel the active Botany Bay trial."))
                .permission("botanybay.command.cancel")
                .executor(cancelExecutor())
                .build();

        final CommandSpec setNpcCommand = CommandSpec.builder()
                .description(Text.of("Set the spawn position for Botany Bay suspects."))
                .permission("botanybay.command.setnpc")
                .executor(setNpcExecutor())
                .build();

        final CommandSpec setZoneCommand = CommandSpec.builder()
                .description(Text.of("Define the Botany Bay voting zone using block selections."))
                .permission("botanybay.command.setzone")
                .executor(setZoneExecutor())
                .build();

        final CommandSpec setBanSignCommand = CommandSpec.builder()
                .description(Text.of("Bind a sign that will display the most recent Botany Bay accusation."))
                .permission("botanybay.command.setbansign")
                .executor(setBanSignExecutor())
                .build();

        final CommandSpec setCommand = CommandSpec.builder()
                .description(Text.of("Configure Botany Bay NPC and voting settings."))
                .child(setNpcCommand, "npc")
                .child(setZoneCommand, "zone")
                .child(setBanSignCommand, "bansign", "sign")
                .executor((src, args) -> {
                    src.sendMessage(Text.of(TextColors.YELLOW,
                            "Usage: /botanybay set <npc|zone|bansign>"));

                    return CommandResult.success();
                })
                .build();

        return Command.builder()
        return CommandSpec.builder()
                .description(Text.of("Manage Botany Bay trials."))
                .child(startCommand, "start", "accuse")
                .child(banCommand, "ban", "queue")
                .child(voteCommand, "vote", "cast")
                .child(statusCommand, "status", "info")
                .child(cancelCommand, "cancel", "end")
                .child(setCommand, "set")
                .child(setZoneCommand, "setzone")
                .child(setBanSignCommand, "setbansign")
                .executor(context -> {
                    context.cause().sendMessage(Component.text()
                            .color(NamedTextColor.YELLOW)
                            .append(Component.text(
                                    "Usage: /botanybay <start|ban|vote|status|cancel|set|setzone|setbansign>"))
                            .build());
                .executor((src, args) -> {
                    src.sendMessage(Text.of(TextColors.YELLOW,
                            "Usage: /botanybay <start|ban|vote|status|cancel|set|setzone|setbansign>"));

                    return CommandResult.success();
                })
                .build();
    }

    private CommandResult executeStart(final CommandContext context) throws CommandException {
        if (this.activeTrial != null) {
            context.cause().sendMessage(Component.text("A Botany Bay trial is already in progress.")
                    .color(NamedTextColor.RED));
            return CommandResult.empty();
        }

        final Optional<User> suspectArgument = context.one(this.suspectParameter);
        final Optional<String> providedReason = context.one(this.reasonParameter);

        final QueuedSuspect trialTarget;
        if (suspectArgument.isPresent()) {
            final User suspectUser = suspectArgument.get();
            final String accusation = providedReason.orElse(DEFAULT_ACCUSATION);
            trialTarget = new QueuedSuspect(suspectUser.uniqueId(), suspectUser.name(), accusation, Instant.now());
            this.removeFromQueue(trialTarget.getSuspectId());
        } else {
            final QueuedSuspect next = this.trialQueue.pollFirst();
            if (next == null) {
                context.cause().sendMessage(Component.text(
                        "No suspects are waiting for trial. Provide a suspect or use /botanybay ban first.")
                        .color(NamedTextColor.RED));
                return CommandResult.empty();
            }
            this.queuedSuspects.remove(next.getSuspectId());
            trialTarget = next;
        }

        this.activeTrial = new TrialSession(trialTarget.getSuspectId(), trialTarget.getSuspectName(),
                trialTarget.getAccusation());
        this.updateBanSign(trialTarget.getSuspectName(), trialTarget.getAccusation());
        this.scheduleConclusion();

        Sponge.server().broadcastAudience().sendMessage(Component.text()
                .append(Component.text("Botany Bay trial has begun!", NamedTextColor.GOLD))
                .append(Component.text(" Accused: ", NamedTextColor.GOLD))
                .append(Component.text(trialTarget.getSuspectName(), NamedTextColor.RED))
                .build());

        Sponge.server().broadcastAudience().sendMessage(Component.text()
                .append(Component.text("Charge: ", NamedTextColor.YELLOW))
                .append(Component.text(trialTarget.getAccusation(), NamedTextColor.WHITE))
                .build());

        Sponge.server().broadcastAudience().sendMessage(Component.text(
                "Vote on the punishment by clicking a choice below or using /botanybay vote <option>:",
                NamedTextColor.AQUA));
        Sponge.server().broadcastAudience().sendMessage(this.formatVoteOptions());

        final Object root = context.cause().root();
        if (root instanceof ServerPlayer) {
            this.logger.info("Botany Bay trial started by {} against {} for '{}'",
                    ((ServerPlayer) root).name(), trialTarget.getSuspectName(), trialTarget.getAccusation());
        } else {
            this.logger.info("Botany Bay trial started against {} for '{}'", trialTarget.getSuspectName(),
                    trialTarget.getAccusation());
        }

        this.despawnActiveNpc();
        return CommandResult.success();
    }

    private CommandResult executeBan(final CommandContext context) throws CommandException {
        final User suspect = context.requireOne(this.suspectParameter);
        final UUID suspectId = suspect.uniqueId();

        if (this.activeTrial != null && this.activeTrial.getSuspectId().equals(suspectId)) {
            context.cause().sendMessage(Component.text(
                    "That suspect is already on trial and cannot be queued again.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        if (this.queuedSuspects.contains(suspectId)) {
            context.cause().sendMessage(Component.text(
                    "That suspect is already waiting in the Botany Bay queue.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final String accusationInput = context.one(this.reasonParameter).orElse(DEFAULT_ACCUSATION);
        final String accusation = accusationInput.trim().isEmpty() ? DEFAULT_ACCUSATION : accusationInput;
        final Component reasonComponent = Component.text(accusation);

        if (Sponge.server().banManager().isBanned(suspect.profile())) {
            context.cause().sendMessage(Component.text(
                    "Suspect is already banned. Adding to the queue regardless.", NamedTextColor.YELLOW));
        } else if (!this.applyProfileBan(suspect, context.cause(), reasonComponent)) {
            context.cause().sendMessage(Component.text(
                    "Unable to ban the suspect automatically. Check the server console for details.",
                    NamedTextColor.RED));
            return CommandResult.empty();
        }

        suspect.player().ifPresent(player ->
                player.kick(Component.text("You have been banished to Botany Bay. Await your public trial.",
                        NamedTextColor.DARK_RED)));

        final QueuedSuspect entry = new QueuedSuspect(suspectId, suspect.name(), accusation, Instant.now());
        this.trialQueue.addLast(entry);
        this.queuedSuspects.add(suspectId);

        Sponge.server().broadcastAudience().sendMessage(Component.text()
                .append(Component.text(suspect.name(), NamedTextColor.DARK_RED))
                .append(Component.text(" has been condemned to await judgment at Botany Bay.", NamedTextColor.GRAY))
                .build());

        context.cause().sendMessage(Component.text()
                .append(Component.text("Queued suspect ", NamedTextColor.GREEN))
                .append(Component.text(suspect.name(), NamedTextColor.YELLOW))
                .append(Component.text(" for a Botany Bay trial. Position in queue: ", NamedTextColor.GREEN))
                .append(Component.text(this.trialQueue.size(), NamedTextColor.AQUA))
                .build());

        final Object root = context.cause().root();
        if (root instanceof ServerPlayer) {
            this.logger.info("{} banned {} and added them to the Botany Bay queue for '{}'.",
                    ((ServerPlayer) root).name(), suspect.name(), accusation);
        } else {
            this.logger.info("Banned {} and added them to the Botany Bay queue for '{}'.", suspect.name(), accusation);
        }

        if (this.npcSpawnLocation.isPresent()) {
            this.spawnSuspectNpc(suspect);
        } else {
            context.cause().sendMessage(Component.text(
                    "No Botany Bay NPC spawn has been set. Use /botanybay set npc.", NamedTextColor.YELLOW));
        }

        if (this.banSignLocation.isPresent()) {
            if (!this.updateBanSign(suspect.name(), accusation)) {
                context.cause().sendMessage(Component.text(
                        "Unable to update the Botany Bay ban sign. Ensure the configured sign still exists.",
                        NamedTextColor.YELLOW));
            }
        } else {
            context.cause().sendMessage(Component.text(
                    "No Botany Bay ban sign has been set. Use /botanybay set bansign.", NamedTextColor.YELLOW));
        }

        return CommandResult.success();
    }

    private boolean applyProfileBan(final User suspect, final CommandCause issuer, final Component reason) {
        final Ban.Builder builder = Ban.builder()
                .type(BanTypes.PROFILE)
                .profile(suspect.profile())
                .reason(reason);

        final Object root = issuer.root();
        if (root instanceof ServerPlayer) {
            builder.source((ServerPlayer) root);
        }

        try {
            final boolean added = Sponge.server().banManager().add(builder.build());
            if (!added) {
                this.logger.warn("Ban manager did not report {} as banned after issuing a Botany Bay ban.",
                        suspect.name());
            }
            return added;
        } catch (final Exception ex) {
            this.logger.error("Failed to apply Botany Bay ban to {}: {}", suspect.name(), ex.getMessage(), ex);
            return false;
        }
    }

    private CommandResult executeVote(final CommandContext context) throws CommandException {
        final CommandCause cause = context.cause();
        if (!(cause.root() instanceof ServerPlayer)) {
            cause.sendMessage(Component.text(
                    "Only players present at Botany Bay may vote.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final ServerPlayer voter = (ServerPlayer) cause.root();

        if (this.activeTrial == null) {
            voter.sendMessage(Component.text("There is no active Botany Bay trial.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        if (voter.uniqueId().equals(this.activeTrial.getSuspectId())) {
            voter.sendMessage(Component.text("The accused cannot vote on their own punishment.",
                    NamedTextColor.RED));
            return CommandResult.empty();
        }

        if (this.voteZone.isPresent() && !this.voteZone.get().contains(voter.location())) {
            voter.sendMessage(Component.text("You must be inside the Botany Bay arena to cast a vote.",
                    NamedTextColor.RED));
            return CommandResult.empty();
        }

        final String requestedOption = context.requireOne(this.punishmentParameter);
        final Optional<PunishmentOption> option = PunishmentOption.fromInput(requestedOption);
        if (!option.isPresent()) {
            voter.sendMessage(Component.text(
                    "Unknown punishment option. Choices are: execute, pillory, release.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final UUID voterId = voter.uniqueId();
        final boolean updatingVote = this.activeTrial.hasVoted(voterId);
        this.activeTrial.castVote(voterId, option.get());

        voter.sendMessage(Component.text()
                .append(Component.text(updatingVote ? "You changed your vote to " : "You voted for ",
                        NamedTextColor.GREEN))
                .append(option.get().displayName())
                .build());
        voter.sendMessage(Component.text()
                .append(Component.text("Current standings: ", NamedTextColor.YELLOW))
                .append(this.formatVoteSummary(this.activeTrial.getVoteCounts()))
                .build());
        return CommandResult.success();
    }

    private CommandResult executeStatus(final CommandContext context) {
        if (this.activeTrial == null) {
            context.cause().sendMessage(Component.text(
                    "No Botany Bay trial is currently running.", NamedTextColor.GRAY));
            return CommandResult.success();
        }

        final Duration elapsed = this.activeTrial.elapsed();
        final long totalSeconds = TRIAL_DURATION.getSeconds();
        final long elapsedSeconds = Math.min(elapsed.getSeconds(), totalSeconds);
        final long remainingSeconds = Math.max(0L, totalSeconds - elapsedSeconds);
        final long minutes = remainingSeconds / 60;
        final long seconds = remainingSeconds % 60;

        context.cause().sendMessage(Component.text("Botany Bay Trial", NamedTextColor.GOLD));
        context.cause().sendMessage(Component.text()
                .append(Component.text("Accused: ", NamedTextColor.YELLOW))
                .append(Component.text(this.activeTrial.getSuspectName(), NamedTextColor.RED))
                .build());
        context.cause().sendMessage(Component.text()
                .append(Component.text("Charge: ", NamedTextColor.YELLOW))
                .append(Component.text(this.activeTrial.getAccusation(), NamedTextColor.WHITE))
                .build());
        context.cause().sendMessage(Component.text()
                .append(Component.text("Time remaining: ", NamedTextColor.YELLOW))
                .append(Component.text(String.format(Locale.ROOT, "%02d:%02d", minutes, seconds),
                        NamedTextColor.WHITE))
                .build());
        context.cause().sendMessage(Component.text()
                .append(Component.text("Votes: ", NamedTextColor.YELLOW))
                .append(this.formatVoteSummary(this.activeTrial.getVoteCounts()))
                .build());
        context.cause().sendMessage(Component.text()
                .append(Component.text("Queue length: ", NamedTextColor.GRAY))
                .append(Component.text(this.trialQueue.size(), NamedTextColor.WHITE))
                .build());
        return CommandResult.success();
    }

    private CommandResult executeCancel(final CommandContext context) {
        if (this.activeTrial == null) {
            context.cause().sendMessage(Component.text(
                    "There is no Botany Bay trial to cancel.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final Component executorName = this.resolveExecutorName(context.cause());
        Sponge.server().broadcastAudience().sendMessage(Component.text()
                .append(Component.text("The Botany Bay trial was dismissed by ", NamedTextColor.GRAY))
                .append(executorName)
                .append(Component.text(".", NamedTextColor.GRAY))
                .build());
        this.cancelScheduledConclusion();
        this.activeTrial = null;
        return CommandResult.success();
    }

    private void scheduleConclusion() {
        this.cancelScheduledConclusion();
        this.conclusionTask = Task.builder()
                .delay(TRIAL_DURATION)
                .plugin(this.pluginContainer)
                .execute(this::concludeTrial)
                .submit(Sponge.server().scheduler());
    }

    private void cancelScheduledConclusion() {
        if (this.conclusionTask != null) {
            this.conclusionTask.cancel();
            this.conclusionTask = null;


    private CommandExecutor startTrialExecutor() {
        return (src, args) -> {
            if (activeTrial != null) {
                src.sendMessage(Text.of(TextColors.RED, "A Botany Bay trial is already in progress."));
                return CommandResult.empty();
            }

            final Optional<User> suspectArgument = args.getOne("suspect");
            final Optional<String> providedReason = args.getOne("reason");

            final QueuedSuspect trialTarget;
            if (suspectArgument.isPresent()) {
                final User suspectUser = suspectArgument.get();
                final String accusation = providedReason.orElse(DEFAULT_ACCUSATION);
                trialTarget = new QueuedSuspect(suspectUser.getUniqueId(), suspectUser.getName(), accusation, Instant.now());
                removeFromQueue(trialTarget.getSuspectId());
            } else {
                final QueuedSuspect next = trialQueue.pollFirst();
                if (next == null) {
                    src.sendMessage(Text.of(TextColors.RED,
                            "No suspects are waiting for trial. Provide a suspect or use /botanybay ban first."));
                    return CommandResult.empty();
                }
                queuedSuspects.remove(next.getSuspectId());
                trialTarget = next;
            }

            activeTrial = new TrialSession(trialTarget.getSuspectId(), trialTarget.getSuspectName(),
                    trialTarget.getAccusation());
            updateBanSign(trialTarget.getSuspectName(), trialTarget.getAccusation());
            scheduleConclusion();

            final Text header = Text.of(TextColors.GOLD, "Botany Bay trial has begun!", TextColors.RESET,
                    " Accused: ", TextColors.RED, trialTarget.getSuspectName());
            Sponge.getServer().getBroadcastChannel().send(header);

            final Text charge = Text.of(TextColors.YELLOW, "Charge: ", TextColors.WHITE, trialTarget.getAccusation());
            Sponge.getServer().getBroadcastChannel().send(charge);

            Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.AQUA,
                    "Vote on the punishment by clicking a choice below or using /botanybay vote <option>:"));
            Sponge.getServer().getBroadcastChannel().send(formatVoteOptions());

            logger.info("Botany Bay trial started by {} against {} for '{}'", src.getName(),
                    trialTarget.getSuspectName(), trialTarget.getAccusation());
            despawnActiveNpc();
            return CommandResult.success();
        };
    }

    private CommandExecutor banExecutor() {
        return (src, args) -> {
            final Optional<BanService> serviceOptional = Sponge.getServiceManager().provide(BanService.class);
            if (!serviceOptional.isPresent()) {
                src.sendMessage(Text.of(TextColors.RED, "Ban service is unavailable. Cannot queue suspect."));
                logger.error("Ban service unavailable while attempting to queue a suspect.");
                return CommandResult.empty();
            }

            final BanService banService = serviceOptional.get();
            final User suspect = args.<User>getOne("suspect").orElse(null);
            if (suspect == null) {
                src.sendMessage(Text.of(TextColors.RED, "Unable to find the suspect."));
                return CommandResult.empty();
            }

            final UUID suspectId = suspect.getUniqueId();
            if (activeTrial != null && activeTrial.getSuspectId().equals(suspectId)) {
                src.sendMessage(Text.of(TextColors.RED,
                        "That suspect is already on trial and cannot be queued again."));
                return CommandResult.empty();
            }

            if (queuedSuspects.contains(suspectId)) {
                src.sendMessage(Text.of(TextColors.RED, "That suspect is already waiting in the Botany Bay queue."));
                return CommandResult.empty();
            }

            final String accusation = args.<String>getOne("reason").orElse(DEFAULT_ACCUSATION);

            if (banService.isBanned(suspect.getProfile())) {
                src.sendMessage(Text.of(TextColors.YELLOW, "Suspect is already banned. Adding to the queue regardless."));

            } else if (!issueBanCommand(banService, suspect, accusation)) {
                src.sendMessage(Text.of(TextColors.RED,
                        "Unable to ban the suspect automatically. Check the server console for details."));
                return CommandResult.empty();

            } else {
                final Ban ban = Ban.builder()
                        .type(BanTypes.PROFILE)
                        .profile(suspect.getProfile())
                        .reason(Text.of(accusation))
                        .build();
                banService.addBan(ban);

            }

            suspect.getPlayer().ifPresent(player -> player.kick(Text.of(TextColors.DARK_RED,
                    "You have been banished to Botany Bay.", TextColors.GRAY, " Await your public trial.")));

            final QueuedSuspect entry = new QueuedSuspect(suspectId, suspect.getName(), accusation, Instant.now());
            trialQueue.addLast(entry);
            queuedSuspects.add(suspectId);

            Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.DARK_RED, suspect.getName(), TextColors.GRAY,
                    " has been condemned to await judgment at Botany Bay."));
            src.sendMessage(Text.of(TextColors.GREEN, "Queued suspect ", TextColors.YELLOW, suspect.getName(),
                    TextColors.GREEN, " for a Botany Bay trial. Position in queue: ", TextColors.AQUA,
                    trialQueue.size()));
            logger.info("{} banned {} and added them to the Botany Bay queue for '{}'.", src.getName(),
                    suspect.getName(), accusation);

            if (npcSpawnLocation.isPresent()) {
                spawnSuspectNpc(suspect);
            } else {
                src.sendMessage(Text.of(TextColors.YELLOW,
                        "No Botany Bay NPC spawn has been set. Use /botanybay set npc."));
            }

            if (banSignLocation.isPresent()) {
                if (!updateBanSign(suspect.getName(), accusation)) {
                    src.sendMessage(Text.of(TextColors.YELLOW,
                            "Unable to update the Botany Bay ban sign. Ensure the configured sign still exists."));
                }
            } else {
                src.sendMessage(Text.of(TextColors.YELLOW,
                        "No Botany Bay ban sign has been set. Use /botanybay set bansign."));
            }
            return CommandResult.success();
        };
    }


    private boolean issueBanCommand(final BanService banService, final User suspect, final String accusation) {
        final StringBuilder command = new StringBuilder("ban ")
                .append(suspect.getName());
        if (accusation != null && !accusation.trim().isEmpty()) {
            command.append(' ').append(accusation);
        }

        final CommandResult result = Sponge.getCommandManager().process(
                Sponge.getServer().getConsole(), command.toString());

        if (banService.isBanned(suspect.getProfile())) {
            return true;
        }

        logger.warn("Ban command '{}' did not apply a ban to {}. Result: {}", command, suspect.getName(), result);
        return false;
    }

    private CommandExecutor voteExecutor() {
        return (src, args) -> {
            if (!(src instanceof Player)) {
                src.sendMessage(Text.of(TextColors.RED, "Only players present at Botany Bay may vote."));
                return CommandResult.empty();
            }

            final Player voter = (Player) src;

            if (activeTrial == null) {
                voter.sendMessage(Text.of(TextColors.RED, "There is no active Botany Bay trial."));
                return CommandResult.empty();
            }

            if (voter.getUniqueId().equals(activeTrial.getSuspectId())) {
                voter.sendMessage(Text.of(TextColors.RED, "The accused cannot vote on their own punishment."));
                return CommandResult.empty();
            }

            if (voteZone.isPresent() && !voteZone.get().contains(voter.getLocation())) {
                voter.sendMessage(Text.of(TextColors.RED,
                        "You must be inside the Botany Bay arena to cast a vote."));
                return CommandResult.empty();
            }

            final String requestedOption = args.<String>getOne("punishment").orElse("");
            final Optional<PunishmentOption> option = PunishmentOption.fromInput(requestedOption);
            if (!option.isPresent()) {
                voter.sendMessage(Text.of(TextColors.RED, "Unknown punishment option. Choices are: execute, pillory, release."));
                return CommandResult.empty();
            }

            final UUID voterId = voter.getUniqueId();
            final boolean updatingVote = activeTrial.hasVoted(voterId);
            activeTrial.castVote(voterId, option.get());

            voter.sendMessage(Text.of(TextColors.GREEN,
                    updatingVote ? "You changed your vote to " : "You voted for ", option.get().toText()));
            voter.sendMessage(Text.of(TextColors.YELLOW, "Current standings: ", formatVoteSummary(activeTrial.getVoteCounts())));
            return CommandResult.success();
        };
    }

    private CommandExecutor statusExecutor() {
        return (src, args) -> {
            if (activeTrial == null) {
                src.sendMessage(Text.of(TextColors.GRAY, "No Botany Bay trial is currently running."));
                return CommandResult.success();
            }

            final Duration elapsed = activeTrial.elapsed();
            final long totalSeconds = TRIAL_DURATION.getSeconds();
            final long elapsedSeconds = Math.min(elapsed.getSeconds(), totalSeconds);
            final long remainingSeconds = Math.max(0L, totalSeconds - elapsedSeconds);
            final long minutes = remainingSeconds / 60;
            final long seconds = remainingSeconds % 60;

            src.sendMessage(Text.of(TextColors.GOLD, "Botany Bay Trial"));
            src.sendMessage(Text.of(TextColors.YELLOW, "Accused: ", TextColors.RED, activeTrial.getSuspectName()));
            src.sendMessage(Text.of(TextColors.YELLOW, "Charge: ", TextColors.WHITE, activeTrial.getAccusation()));
            src.sendMessage(Text.of(TextColors.YELLOW, "Time remaining: ", TextColors.WHITE,
                    String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)));
            src.sendMessage(Text.of(TextColors.YELLOW, "Votes: ", formatVoteSummary(activeTrial.getVoteCounts())));
            src.sendMessage(Text.of(TextColors.GRAY, "Queue length: ", TextColors.WHITE, trialQueue.size()));
            return CommandResult.success();
        };
    }

    private CommandExecutor cancelExecutor() {
        return (src, args) -> {
            if (activeTrial == null) {
                src.sendMessage(Text.of(TextColors.RED, "There is no Botany Bay trial to cancel."));
                return CommandResult.empty();
            }

            Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GRAY,
                    "The Botany Bay trial was dismissed by ", TextColors.WHITE, src.getName(), TextColors.GRAY, "."));
            cancelScheduledConclusion();
            activeTrial = null;
            return CommandResult.success();
        };
    }

    private void scheduleConclusion() {
        cancelScheduledConclusion();
        conclusionTask = Sponge.getScheduler().createTaskBuilder()
                .delay(TRIAL_DURATION.getSeconds(), TimeUnit.SECONDS)
                .execute(() -> concludeTrial())
                .submit(this);
    }

    private void cancelScheduledConclusion() {
        if (conclusionTask != null) {
            conclusionTask.cancel();
            conclusionTask = null;
        }
    }

    private void concludeTrial() {
        if (this.activeTrial == null) {
            return;
        }

        final TrialSession session = this.activeTrial;
        this.activeTrial = null;
        this.conclusionTask = null;

        final Map<PunishmentOption, Integer> votes = session.getVoteCounts();
        final PunishmentOption outcome = this.determineOutcome(votes);

        Sponge.server().broadcastAudience().sendMessage(Component.text()
                .append(Component.text("The Botany Bay trial of ", NamedTextColor.GOLD))
                .append(Component.text(session.getSuspectName(), NamedTextColor.RED))
                .append(Component.text(" has concluded!", NamedTextColor.GOLD))
                .build());
        Sponge.server().broadcastAudience().sendMessage(Component.text()
                .append(Component.text("Final vote tally: ", NamedTextColor.YELLOW))
                .append(this.formatVoteSummary(votes))
                .build());

        final Component verdict;
        switch (outcome) {
            case EXECUTE:
                verdict = Component.text("Verdict: Execution! (Roleplay your dramatic finishing moves.)",
                        NamedTextColor.DARK_RED);
                break;
            case PILLORY:
                verdict = Component.text("Verdict: Pillory! (Escort the culprit to the stocks.)",
                        NamedTextColor.GOLD);
                break;
            default:
                verdict = Component.text("Verdict: Release! (The crowd shows mercy today.)",
                        NamedTextColor.GREEN);
                break;
        }
        Sponge.server().broadcastAudience().sendMessage(verdict);

        if (activeTrial == null) {
            return;
        }

        final TrialSession session = activeTrial;
        activeTrial = null;
        conclusionTask = null;

        final Map<PunishmentOption, Integer> votes = session.getVoteCounts();
        final PunishmentOption outcome = determineOutcome(votes);

        Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GOLD,
                "The Botany Bay trial of ", TextColors.RED, session.getSuspectName(), TextColors.GOLD,
                " has concluded!"));
        Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.YELLOW, "Final vote tally: ",
                formatVoteSummary(votes)));

        switch (outcome) {
            case EXECUTE:
                Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.DARK_RED,
                        "Verdict: Execution!", TextColors.GRAY, " (Roleplay your dramatic finishing moves.)"));
                break;
            case PILLORY:
                Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GOLD,
                        "Verdict: Pillory!", TextColors.GRAY, " (Escort the culprit to the stocks.)"));
                break;
            default:
                Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GREEN,
                        "Verdict: Release!", TextColors.GRAY, " (The crowd shows mercy today.)"));
                break;
        }

    }

    private PunishmentOption determineOutcome(final Map<PunishmentOption, Integer> votes) {
        PunishmentOption selected = PunishmentOption.RELEASE;
        int highest = -1;
        boolean tie = false;

        for (final Map.Entry<PunishmentOption, Integer> entry : votes.entrySet()) {
            final int count = entry.getValue();
            if (count > highest) {
                highest = count;
                selected = entry.getKey();
                tie = false;
            } else if (count == highest) {
                tie = true;
            }
        }

        if (tie) {
            return PunishmentOption.RELEASE;
        }

        return selected;
    }
    private CommandResult executeSetNpc(final CommandContext context) {
        final Object root = context.cause().root();
        if (!(root instanceof ServerPlayer)) {
            context.cause().sendMessage(Component.text(
                    "Only players can set the Botany Bay NPC location.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final ServerPlayer player = (ServerPlayer) root;
        final Vector3i blockPos = player.location().blockPosition();
        final Vector3d centered = new Vector3d(blockPos.x() + 0.5, blockPos.y(), blockPos.z() + 0.5);
        final ServerLocation location = ServerLocation.of(player.world(), centered);
        this.npcSpawnLocation = Optional.of(location);
        this.despawnActiveNpc();

        player.sendMessage(Component.text()
                .append(Component.text("Botany Bay NPC spawn set at ", NamedTextColor.GREEN))
                .append(Component.text(this.formatBlockPosition(blockPos), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN))
                .build());
        return CommandResult.success();
    }

    private CommandResult executeSetZone(final CommandContext context) {
        final Object root = context.cause().root();
        if (!(root instanceof ServerPlayer)) {
            context.cause().sendMessage(Component.text(
                    "Only players can define the Botany Bay zone.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final ServerPlayer player = (ServerPlayer) root;
        final ZoneSelection selection = new ZoneSelection(player.world().uniqueId());
        this.pendingZoneSelections.put(player.uniqueId(), selection);
        player.sendMessage(Component.text("Left click a block for the first corner, right click for the second.",
                NamedTextColor.YELLOW));
        return CommandResult.success();
    }

    private CommandResult executeSetBanSign(final CommandContext context) {
        final Object root = context.cause().root();
        if (!(root instanceof ServerPlayer)) {
            context.cause().sendMessage(Component.text(
                    "Only players can bind the Botany Bay ban sign.", NamedTextColor.RED));
            return CommandResult.empty();
        }

        final ServerPlayer player = (ServerPlayer) root;
        this.pendingBanSignSelections.add(player.uniqueId());
        player.sendMessage(Component.text("Right click the sign that should display the latest accusation.",
                NamedTextColor.YELLOW));
        return CommandResult.success();
    }

    @Listener
    public void onPrimaryInteract(final InteractBlockEvent.Primary event, @Root final ServerPlayer player) {
        final ZoneSelection selection = this.pendingZoneSelections.get(player.uniqueId());
        if (selection == null) {
            return;
        }

        event.block().location().ifPresent(location -> {
            if (!location.world().uniqueId().equals(selection.worldId)) {
                player.sendMessage(Component.text("Selections must remain in the same world.",
                        NamedTextColor.RED));
                return;
            }

            selection.firstCorner = location.blockPosition();
            player.sendMessage(Component.text()
                    .append(Component.text("First corner set at ", NamedTextColor.GREEN))
                    .append(Component.text(this.formatBlockPosition(selection.firstCorner), NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN))
                    .build());
            this.tryFinalizeZone(player, selection);
        });
    }

    @Listener
    public void onSecondaryInteract(final InteractBlockEvent.Secondary event, @Root final ServerPlayer player) {
        if (this.pendingBanSignSelections.remove(player.uniqueId())) {
            event.block().location().ifPresent(location -> {
                final Optional<Sign> sign = location.blockEntity(Sign.class);
                if (!sign.isPresent()) {
                    player.sendMessage(Component.text("That block is not a sign.", NamedTextColor.RED));
                    this.pendingBanSignSelections.add(player.uniqueId());
                    return;
                }

                this.banSignLocation = Optional.of(location);
                player.sendMessage(Component.text("Ban sign bound successfully!", NamedTextColor.GREEN));
                this.resetBanSignMessage();
            });
            return;
        }

        final ZoneSelection selection = this.pendingZoneSelections.get(player.uniqueId());


    private CommandExecutor setNpcExecutor() {
        return (src, args) -> {
            if (!(src instanceof Player)) {
                src.sendMessage(Text.of(TextColors.RED, "Only players can set the Botany Bay NPC location."));
                return CommandResult.empty();
            }

            final Player player = (Player) src;
            final Vector3i blockPos = player.getLocation().getBlockPosition();
            Location<World> location = new Location<>(player.getWorld(), blockPos);
            location = location.add(0.5, 0, 0.5);
            npcSpawnLocation = Optional.of(location);
            despawnActiveNpc();

            player.sendMessage(Text.of(TextColors.GREEN, "Botany Bay NPC spawn set at ", TextColors.YELLOW,
                    formatBlockPosition(blockPos), TextColors.GREEN, "."));
            return CommandResult.success();
        };
    }

    private CommandExecutor setZoneExecutor() {
        return (src, args) -> {
            if (!(src instanceof Player)) {
                src.sendMessage(Text.of(TextColors.RED, "Only players can configure the Botany Bay voting zone."));
                return CommandResult.empty();
            }

            final Player player = (Player) src;
            final UUID playerId = player.getUniqueId();
            pendingZoneSelections.put(playerId, new ZoneSelection(player.getWorld().getUniqueId()));
            player.sendMessage(Text.of(TextColors.GOLD,
                    "Left-click a block to set the first corner, then right-click to set the opposite corner."));
            player.sendMessage(Text.of(TextColors.YELLOW,
                    "When finished, the Botany Bay vote zone will be updated."));
            return CommandResult.success();
        };
    }

    private CommandExecutor setBanSignExecutor() {
        return (src, args) -> {
            if (!(src instanceof Player)) {
                src.sendMessage(Text.of(TextColors.RED, "Only players can bind the Botany Bay ban sign."));
                return CommandResult.empty();
            }

            final Player player = (Player) src;
            pendingBanSignSelections.add(player.getUniqueId());
            player.sendMessage(Text.of(TextColors.GOLD,
                    "Right-click the sign that should display Botany Bay accusations."));
            player.sendMessage(Text.of(TextColors.YELLOW,
                    "The sign will update with the latest ban reason when suspects are queued."));
            return CommandResult.success();
        };
    }

    @Listener
    public void onPrimaryInteract(final InteractBlockEvent.Primary event) {
        if (event.getHandType() != HandTypes.MAIN_HAND) {
            return;
        }
        event.getCause().first(Player.class).ifPresent(player ->
                event.getTargetBlock().getLocation().ifPresent(location ->
                        handleZoneSelection(player, location, true)));
    }

    @Listener
    public void onSecondaryInteract(final InteractBlockEvent.Secondary event) {
        if (event.getHandType() != HandTypes.MAIN_HAND) {
            return;
        }
        event.getCause().first(Player.class).ifPresent(player ->
                event.getTargetBlock().getLocation().ifPresent(location -> {
                    handleBanSignSelection(player, location);
                    handleZoneSelection(player, location, false);
                }));
    }

    private void handleZoneSelection(final Player player, final Location<World> location, final boolean primary) {
        final ZoneSelection selection = pendingZoneSelections.get(player.getUniqueId());

        if (selection == null) {
            return;
        }

        event.block().location().ifPresent(location -> {
            if (!location.world().uniqueId().equals(selection.worldId)) {
                player.sendMessage(Component.text("Selections must remain in the same world.",
                        NamedTextColor.RED));
                return;
            }

            selection.secondCorner = location.blockPosition();
            player.sendMessage(Component.text()
                    .append(Component.text("Second corner set at ", NamedTextColor.GREEN))
                    .append(Component.text(this.formatBlockPosition(selection.secondCorner), NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN))
                    .build());
            this.tryFinalizeZone(player, selection);
        });
    }

    private void tryFinalizeZone(final ServerPlayer player, final ZoneSelection selection) {
        if (selection.firstCorner == null || selection.secondCorner == null) {
            return;
        }

        final Vector3i min = new Vector3i(
                Math.min(selection.firstCorner.x(), selection.secondCorner.x()),
                Math.min(selection.firstCorner.y(), selection.secondCorner.y()),
                Math.min(selection.firstCorner.z(), selection.secondCorner.z()));
        final Vector3i max = new Vector3i(
                Math.max(selection.firstCorner.x(), selection.secondCorner.x()),
                Math.max(selection.firstCorner.y(), selection.secondCorner.y()),
                Math.max(selection.firstCorner.z(), selection.secondCorner.z()));

        this.voteZone = Optional.of(new VoteZone(selection.worldId, min, max));
        this.pendingZoneSelections.remove(player.uniqueId());
        player.sendMessage(Component.text("Botany Bay voting zone saved!", NamedTextColor.GREEN));
    }

    private void spawnSuspectNpc(final User suspect) {
        if (!this.npcSpawnLocation.isPresent()) {
            return;
        }

        final ServerLocation location = this.npcSpawnLocation.get();
        final ServerWorld world = location.world();
        final Vector3d position = location.position();

        final Entity entity = world.createEntity(EntityTypes.HUMAN.get(), position);
        if (!(entity instanceof Humanoid)) {
            this.logger.warn("Failed to create a humanoid NPC for {}.", suspect.name());
            return;
        }

        final Humanoid humanoid = (Humanoid) entity;
        final GameProfile profile = suspect.profile();
        humanoid.offer(Keys.GAME_PROFILE, profile);

        if (world.spawnEntity(humanoid)) {
            this.npcEntityId = humanoid.uniqueId();
        } else {

            this.logger.warn("Unable to spawn Botany Bay NPC for {} at {}.", suspect.name(), position);

            this.logger.warn("Unable to spawn Botany Bay NPC for {} at {}.", suspect.name(), position)
        if (!selection.getWorldId().equals(location.getExtent().getUniqueId())) {
            player.sendMessage(Text.of(TextColors.RED,
                    "Selections must be made in the same world. Restart with /botanybay setzone."));
            pendingZoneSelections.remove(player.getUniqueId());
            return;
        }

        final Vector3i blockPos = location.getBlockPosition();
        if (primary) {
            selection.setFirstCorner(blockPos);
            player.sendMessage(Text.of(TextColors.GREEN, "First corner set at ", TextColors.YELLOW,
                    formatBlockPosition(blockPos), TextColors.GREEN, "."));
        } else {
            selection.setSecondCorner(blockPos);
            player.sendMessage(Text.of(TextColors.GREEN, "Second corner set at ", TextColors.YELLOW,
                    formatBlockPosition(blockPos), TextColors.GREEN, "."));
        }

        if (selection.isComplete()) {
            voteZone = Optional.of(selection.toZone());
            pendingZoneSelections.remove(player.getUniqueId());
            player.sendMessage(Text.of(TextColors.GOLD, "Botany Bay voting zone updated."));
        }
    }

    private void handleBanSignSelection(final Player player, final Location<World> location) {
        if (!pendingBanSignSelections.remove(player.getUniqueId())) {
            return;
        }

        final Optional<Sign> signOptional = location.getTileEntity(Sign.class);
        if (!signOptional.isPresent()) {
            player.sendMessage(Text.of(TextColors.RED,
                    "That block is not a sign. Run /botanybay set bansign and try again."));
            return;
        }

        banSignLocation = Optional.of(location);
        player.sendMessage(Text.of(TextColors.GREEN, "Botany Bay ban sign bound at ", TextColors.YELLOW,
                formatBlockPosition(location.getBlockPosition()), TextColors.GREEN, "."));
        resetBanSignMessage();
    }

    private void spawnSuspectNpc(final User suspect) {
        if (!npcSpawnLocation.isPresent()) {
            return;
        }

        final Location<World> location = npcSpawnLocation.get();
        final World world = location.getExtent();

        despawnActiveNpc();

        final Human human = (Human) world.createEntity(EntityTypes.HUMAN, location.getPosition());
        human.offer(Keys.GAME_PROFILE, suspect.getProfile());
        human.offer(Keys.DISPLAY_NAME, Text.of(TextColors.YELLOW, suspect.getName()));
        human.offer(Keys.AI_ENABLED, false);
        human.offer(Keys.INVULNERABLE, true);
        human.setLocation(location);

        if (world.spawnEntity(human)) {
            npcEntityId = human.getUniqueId();
        } else {
            npcEntityId = null;
            logger.warn("Failed to spawn Botany Bay NPC for {} at {}", suspect.getName(), formatBlockPosition(location.getBlockPosition()));

        }
    }

    private void despawnActiveNpc() {
        if (this.npcEntityId == null) {
            return;
        }

        for (final ServerWorld world : Sponge.server().worldManager().worlds()) {
            world.entity(this.npcEntityId).ifPresent(Entity::remove);
        }
        this.npcEntityId = null;
    }

    private boolean updateBanSign(final String suspectName, final String accusation) {
        final Optional<Sign> signOptional = this.getBanSign();

        if (npcEntityId == null) {
            return;
        }

        for (final World world : Sponge.getServer().getWorlds()) {
            final Optional<Entity> entity = world.getEntity(npcEntityId);
            if (entity.isPresent()) {
                entity.get().remove();
                break;
            }
        }

        npcEntityId = null;
    }

    private String formatBlockPosition(final Vector3i position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private boolean updateBanSign(final String suspectName, final String accusation) {
        final Optional<Sign> signOptional = getBanSign();

        if (!signOptional.isPresent()) {
            return false;
        }

        final List<String> accusationLines = this.wrapAccusationLines(accusation);
        final String nameLine = this.trimForSign(suspectName);

        final List<Component> lines = new ArrayList<>(4);
        lines.add(Component.text(nameLine, NamedTextColor.DARK_RED));
        lines.add(Component.text("Charge:", NamedTextColor.GOLD));
        lines.add(Component.text(accusationLines.get(0), NamedTextColor.WHITE));
        lines.add(Component.text(accusationLines.size() > 1 ? accusationLines.get(1) : "", NamedTextColor.WHITE));

        signOptional.get().offer(Keys.SIGN_LINES, lines);


        final List<String> accusationLines = wrapAccusationLines(accusation);
        final String nameLine = trimForSign(suspectName);
        final Text lineOne = Text.of(TextColors.DARK_RED, nameLine);
        final Text lineTwo = Text.of(TextColors.GOLD, "Charge:");
        final Text lineThree = Text.of(TextColors.WHITE, accusationLines.get(0));
        final Text lineFour = Text.of(TextColors.WHITE, accusationLines.size() > 1 ? accusationLines.get(1) : "");

        signOptional.get().offer(Keys.SIGN_LINES, Arrays.asList(lineOne, lineTwo, lineThree, lineFour));

        return true;
    }

    private void resetBanSignMessage() {
        final Optional<Sign> signOptional = this.getBanSign();

        final Optional<Sign> signOptional = getBanSign();

        if (!signOptional.isPresent()) {
            return;
        }

        final List<Component> defaultLines = List.of(
                Component.text("Botany Bay", NamedTextColor.DARK_GREEN),
                Component.text("Awaiting", NamedTextColor.YELLOW),
                Component.text("Accused", NamedTextColor.YELLOW),
                Component.text("")

        final List<Text> defaultLines = Arrays.asList(
                Text.of(TextColors.DARK_GREEN, "Botany Bay"),
                Text.of(TextColors.YELLOW, "Awaiting"),
                Text.of(TextColors.YELLOW, "Accused"),
                Text.of("")

        );
        signOptional.get().offer(Keys.SIGN_LINES, defaultLines);
    }

    private Optional<Sign> getBanSign() {
        if (!this.banSignLocation.isPresent()) {
            return Optional.empty();
        }

        final ServerLocation location = this.banSignLocation.get();
        final Optional<Sign> signOptional = location.blockEntity(Sign.class);
        if (!signOptional.isPresent()) {
            this.logger.warn("Configured Botany Bay ban sign is missing at {}",
                    this.formatBlockPosition(location.blockPosition()));

        if (!banSignLocation.isPresent()) {
            return Optional.empty();
        }

        final Location<World> location = banSignLocation.get();
        final Optional<Sign> signOptional = location.getTileEntity(Sign.class);
        if (!signOptional.isPresent()) {
            logger.warn("Configured Botany Bay ban sign is missing at {}", formatBlockPosition(location.getBlockPosition()));

        }
        return signOptional;
    }

    private List<String> wrapAccusationLines(final String accusation) {
        final String sanitized = Optional.ofNullable(accusation).orElse("").trim();
        if (sanitized.isEmpty()) {
            return List.of("");

            return Arrays.asList("");

        }

        final List<String> lines = new ArrayList<>();
        int index = 0;
        while (index < sanitized.length() && lines.size() < 2) {
            final int end = Math.min(sanitized.length(), index + SIGN_LINE_LENGTH);
            lines.add(sanitized.substring(index, end));
            index = end;
        }

        if (sanitized.length() > SIGN_LINE_LENGTH * 2) {
            final String second = lines.get(1);
            final int trimLength = Math.max(0, SIGN_LINE_LENGTH - 3);
            final String truncated = second.length() > trimLength
                    ? second.substring(0, trimLength) + "..."
                    : (second + "...").substring(0, Math.min(second.length() + 3, SIGN_LINE_LENGTH));
            lines.set(1, truncated);
        }
        return lines;
    }

    private String trimForSign(final String value) {
        final String sanitized = Optional.ofNullable(value).orElse("").trim();
        if (sanitized.length() <= SIGN_LINE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, SIGN_LINE_LENGTH - 3) + "...";
    }

    private Component formatVoteOptions() {
        final Component execute = PunishmentOption.EXECUTE.displayName()
                .clickEvent(ClickEvent.runCommand("/botanybay vote " + PunishmentOption.EXECUTE.getId()))
                .hoverEvent(HoverEvent.showText(Component.text(PunishmentOption.EXECUTE.getDescription())));

        final Component pillory = PunishmentOption.PILLORY.displayName()
                .clickEvent(ClickEvent.runCommand("/botanybay vote " + PunishmentOption.PILLORY.getId()))
                .hoverEvent(HoverEvent.showText(Component.text(PunishmentOption.PILLORY.getDescription())));

        final Component release = PunishmentOption.RELEASE.displayName()
                .clickEvent(ClickEvent.runCommand("/botanybay vote " + PunishmentOption.RELEASE.getId()))
                .hoverEvent(HoverEvent.showText(Component.text(PunishmentOption.RELEASE.getDescription())));

        return Component.join(JoinConfiguration.separator(Component.text(" | ", NamedTextColor.GRAY)),
                execute, pillory, release);
    }

    private Component formatVoteSummary(final Map<PunishmentOption, Integer> counts) {
        return Component.join(JoinConfiguration.separator(Component.text(" | ", NamedTextColor.GRAY)),
                Component.text("Execution: ", NamedTextColor.DARK_RED)
                        .append(Component.text(counts.getOrDefault(PunishmentOption.EXECUTE, 0),
                                NamedTextColor.WHITE)),
                Component.text("Pillory: ", NamedTextColor.GOLD)
                        .append(Component.text(counts.getOrDefault(PunishmentOption.PILLORY, 0),
                                NamedTextColor.WHITE)),
                Component.text("Release: ", NamedTextColor.GREEN)
                        .append(Component.text(counts.getOrDefault(PunishmentOption.RELEASE, 0),
                                NamedTextColor.WHITE)));
    }

    private void removeFromQueue(final UUID suspectId) {
        if (!this.queuedSuspects.remove(suspectId)) {
            return;
        }
        final Iterator<QueuedSuspect> iterator = this.trialQueue.iterator();


    private Text formatVoteOptions() {
        final Text execute = Text.builder()
                .append(PunishmentOption.EXECUTE.toText())
                .onClick(TextActions.runCommand("/botanybay vote " + PunishmentOption.EXECUTE.getId()))
                .onHover(TextActions.showText(Text.of(PunishmentOption.EXECUTE.getDescription())))
                .build();

        final Text pillory = Text.builder()
                .append(PunishmentOption.PILLORY.toText())
                .onClick(TextActions.runCommand("/botanybay vote " + PunishmentOption.PILLORY.getId()))
                .onHover(TextActions.showText(Text.of(PunishmentOption.PILLORY.getDescription())))
                .build();

        final Text release = Text.builder()
                .append(PunishmentOption.RELEASE.toText())
                .onClick(TextActions.runCommand("/botanybay vote " + PunishmentOption.RELEASE.getId()))
                .onHover(TextActions.showText(Text.of(PunishmentOption.RELEASE.getDescription())))
                .build();

        return Text.joinWith(Text.of(TextColors.GRAY, " | "), execute, pillory, release);
    }

    private Text formatVoteSummary(final Map<PunishmentOption, Integer> counts) {
        return Text.joinWith(Text.of(TextColors.GRAY, " | "),
                Text.of(PunishmentOption.EXECUTE.toText(), TextColors.WHITE, ": ", counts.getOrDefault(PunishmentOption.EXECUTE, 0)),
                Text.of(PunishmentOption.PILLORY.toText(), TextColors.WHITE, ": ", counts.getOrDefault(PunishmentOption.PILLORY, 0)),
                Text.of(PunishmentOption.RELEASE.toText(), TextColors.WHITE, ": ", counts.getOrDefault(PunishmentOption.RELEASE, 0))
        );
    }

    private void removeFromQueue(final UUID suspectId) {
        if (!queuedSuspects.remove(suspectId)) {
            return;
        }
        final Iterator<QueuedSuspect> iterator = trialQueue.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().getSuspectId().equals(suspectId)) {
                iterator.remove();
                break;
            }
        }
    }

    private String formatBlockPosition(final Vector3i position) {
        return position.x() + ", " + position.y() + ", " + position.z();
    }

    private Component resolveExecutorName(final CommandCause cause) {
        final Object root = cause.root();
        if (root instanceof ServerPlayer) {
            return Component.text(((ServerPlayer) root).name(), NamedTextColor.WHITE);
        }
        return Component.text("Console", NamedTextColor.WHITE);
    }

    private static final class ZoneSelection {
        private final UUID worldId;
        private Vector3i firstCorner;
        private Vector3i secondCorner;

        private ZoneSelection(final UUID worldId) {
            this.worldId = worldId;
        }



        private UUID getWorldId() {
            return worldId;
        }

        private void setFirstCorner(final Vector3i firstCorner) {
            this.firstCorner = firstCorner;
        }

        private void setSecondCorner(final Vector3i secondCorner) {
            this.secondCorner = secondCorner;
        }

        private boolean isComplete() {
            return firstCorner != null && secondCorner != null;
        }

        private VoteZone toZone() {
            if (!isComplete()) {
                throw new IllegalStateException("Zone selection is incomplete.");
            }

            final Vector3i min = new Vector3i(
                    Math.min(firstCorner.getX(), secondCorner.getX()),
                    Math.min(firstCorner.getY(), secondCorner.getY()),
                    Math.min(firstCorner.getZ(), secondCorner.getZ()));
            final Vector3i max = new Vector3i(
                    Math.max(firstCorner.getX(), secondCorner.getX()),
                    Math.max(firstCorner.getY(), secondCorner.getY()),
                    Math.max(firstCorner.getZ(), secondCorner.getZ()));

            return new VoteZone(worldId, min, max);
        }

    }
}
