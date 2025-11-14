package com.botanybay;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import org.spongepowered.api.util.ban.Ban;
import org.spongepowered.api.util.ban.BanTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

@Plugin("botanybay")
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
                    return CommandResult.success();
                })
                .build();

        return Command.builder()
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
        return true;
    }

    private void resetBanSignMessage() {
        final Optional<Sign> signOptional = this.getBanSign();
        if (!signOptional.isPresent()) {
            return;
        }

        final List<Component> defaultLines = List.of(
                Component.text("Botany Bay", NamedTextColor.DARK_GREEN),
                Component.text("Awaiting", NamedTextColor.YELLOW),
                Component.text("Accused", NamedTextColor.YELLOW),
                Component.text("")
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
        }
        return signOptional;
    }

    private List<String> wrapAccusationLines(final String accusation) {
        final String sanitized = Optional.ofNullable(accusation).orElse("").trim();
        if (sanitized.isEmpty()) {
            return List.of("");
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
    }
}
