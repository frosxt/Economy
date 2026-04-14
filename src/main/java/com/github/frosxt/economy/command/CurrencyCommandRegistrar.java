package com.github.frosxt.economy.command;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.SubcommandToggles;
import com.github.frosxt.economy.command.sub.*;
import com.github.frosxt.economy.leaderboard.render.LeaderboardMenuRenderer;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.*;
import com.github.frosxt.prisoncore.scheduler.api.TaskOrchestrator;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class CurrencyCommandRegistrar {
    private static final String NAMESPACE = "economy";

    private final EconomyService economy;
    private final CommandService commandService;
    private final EconomyMessageDispatcher messages;
    private final LeaderboardMenuRenderer leaderboardRenderer;
    private final TaskOrchestrator orchestrator;
    private final Logger logger;
    private final List<CommandKey> registeredKeys = new ArrayList<>();

    public CurrencyCommandRegistrar(
            final EconomyService economy,
            final CommandService commandService,
            final EconomyMessageDispatcher messages,
            final LeaderboardMenuRenderer leaderboardRenderer,
            final TaskOrchestrator orchestrator,
            final Logger logger) {
        this.economy = economy;
        this.commandService = commandService;
        this.messages = messages;
        this.leaderboardRenderer = leaderboardRenderer;
        this.orchestrator = orchestrator;
        this.logger = logger;
    }

    public void register(final CurrencyDefinition definition) {
        final String base = definition.command().baseCommand();
        final String root = definition.command().permissionRoot();
        final SubcommandToggles toggles = definition.command().subcommands();

        final CommandDescriptor.Builder builder = CommandDescriptor.builder(NAMESPACE, base)
                .aliases(definition.command().aliases().toArray(new String[0]))
                .description(definition.command().description())
                .permission(PermissionPolicy.of(root));

        if (toggles.help()) {
            builder.subcommand(new HelpSubcommand(definition, messages, root).build(NAMESPACE));
        }
        if (toggles.balance()) {
            builder.subcommand(new BalanceSubcommand(economy, definition, messages, root).build(NAMESPACE));
        }
        if (toggles.give()) {
            builder.subcommand(new GiveSubcommand(economy, definition, messages, root).build(NAMESPACE));
        }
        if (toggles.take()) {
            builder.subcommand(new TakeSubcommand(economy, definition, messages, root).build(NAMESPACE));
        }
        if (toggles.set()) {
            builder.subcommand(new SetSubcommand(economy, definition, messages, root).build(NAMESPACE));
        }
        if (toggles.pay()) {
            builder.subcommand(new PaySubcommand(economy, definition, messages, root).build(NAMESPACE));
        }
        if (toggles.top()) {
            builder.subcommand(new TopSubcommand(economy, definition, messages, leaderboardRenderer, root).build(NAMESPACE));
        }
        if (toggles.recalc()) {
            builder.subcommand(new RecalcSubcommand(economy, definition, messages, orchestrator, root).build(NAMESPACE));
        }
        if (toggles.withdraw()) {
            builder.subcommand(new WithdrawSubcommand(economy, definition, messages, root).build(NAMESPACE));
        }
        if (toggles.payToggle()) {
            builder.subcommand(new PayToggleSubcommand(economy, definition, messages, root).build(NAMESPACE));
        }

        builder.executor(ctx -> {
            final CommandSender sender = (CommandSender) ctx.sender();
            definition.messages().find("player-help").ifPresent(msg -> messages.send(sender, msg, Map.of()));
            return new CommandResult.Success(null);
        });

        final CommandDescriptor descriptor = builder.build();
        commandService.register(descriptor);
        registeredKeys.add(new CommandKey(NAMESPACE, base));
        logger.info("[Economy] Registered command /" + base + " for currency " + definition.key().value());
    }

    public void unregisterAll() {
        for (final CommandKey key : registeredKeys) {
            commandService.unregister(key);
        }
        registeredKeys.clear();
    }
}
