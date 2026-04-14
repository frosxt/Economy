package com.github.frosxt.economy.bootstrap;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.bootstrap.config.EconomyConfig;
import com.github.frosxt.economy.command.CurrencyCommandRegistrar;
import com.github.frosxt.economy.currency.CurrencyRegistry;
import com.github.frosxt.economy.currency.loader.CurrencyLoader;
import com.github.frosxt.economy.currency.loader.CurrencyValidator;
import com.github.frosxt.economy.currency.provider.CurrencyProvider;
import com.github.frosxt.economy.currency.provider.impl.ExperienceCurrencyProvider;
import com.github.frosxt.economy.currency.provider.impl.MoneyCurrencyProvider;
import com.github.frosxt.economy.currency.provider.impl.VirtualCurrencyProvider;
import com.github.frosxt.economy.leaderboard.LeaderboardService;
import com.github.frosxt.economy.leaderboard.render.LeaderboardMenuRenderer;
import com.github.frosxt.economy.leaderboard.strategy.BalanceBackedLeaderboardStrategy;
import com.github.frosxt.economy.leaderboard.strategy.LeaderboardStrategy;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.economy.placeholder.EconomyPlaceholders;
import com.github.frosxt.economy.service.DefaultEconomyService;
import com.github.frosxt.economy.storage.backend.EconomyBackend;
import com.github.frosxt.economy.storage.backend.factory.EconomyBackendFactory;
import com.github.frosxt.economy.storage.store.BalanceStore;
import com.github.frosxt.economy.storage.store.LedgerStore;
import com.github.frosxt.economy.storage.store.PaymentToggleStore;
import com.github.frosxt.economy.vault.VaultEconomyProvider;
import com.github.frosxt.economy.withdraw.WithdrawNoteService;
import com.github.frosxt.economy.withdraw.listener.WithdrawNoteListener;
import com.github.frosxt.prisoncore.api.capability.CapabilityKey;
import com.github.frosxt.prisoncore.api.module.ModuleContext;
import com.github.frosxt.prisoncore.api.module.support.AbstractPlatformModule;
import com.github.frosxt.prisoncore.command.api.CommandService;
import com.github.frosxt.prisoncore.commons.bukkit.event.BukkitListenerHost;
import com.github.frosxt.prisoncore.kernel.config.CoreConfig;
import com.github.frosxt.prisoncore.kernel.storage.StorageRegistry;
import com.github.frosxt.prisoncore.menu.api.MenuService;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderService;
import com.github.frosxt.prisoncore.scheduler.api.TaskHandle;
import com.github.frosxt.prisoncore.scheduler.api.TaskOrchestrator;
import com.github.frosxt.prisoncore.scheduler.api.TaskSpec;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EconomyModule extends AbstractPlatformModule {
    private static final List<String> BUNDLED_CURRENCIES =
            List.of("money.yml", "exp.yml", "tokens.yml", "shards.yml");

    private Logger logger;
    private Path dataFolder;
    private Path currenciesDir;

    private EconomyConfig config;
    private CurrencyRegistry registry;
    private EconomyBackend backend;
    private BalanceStore balanceStore;
    private LedgerStore ledgerStore;
    private PaymentToggleStore paymentToggleStore;
    private LeaderboardService leaderboardService;
    private EconomyService economyService;
    private CurrencyCommandRegistrar commandRegistrar;
    private EconomyPlaceholders placeholders;
    private WithdrawNoteListener withdrawListener;
    private BukkitListenerHost listenerHost;
    private VaultEconomyProvider vaultProvider;
    private TaskHandle recalcTask;
    private TaskHandle flushTask;

    @Override
    protected void onPrepare(final ModuleContext context) {
        this.logger = context.logger();
        this.dataFolder = context.dataFolder();
        this.currenciesDir = dataFolder.resolve("currencies");

        try {
            Files.createDirectories(dataFolder);
            Files.createDirectories(currenciesDir);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to create Economy folder layout", e);
        }

        copyBundledResourceIfMissing("/config.yml", dataFolder.resolve("config.yml"));
        for (final String bundled : BUNDLED_CURRENCIES) {
            copyBundledResourceIfMissing("/currencies/" + bundled, currenciesDir.resolve(bundled));
        }

        this.config = EconomyConfig.load(dataFolder.resolve("config.yml"), logger);

        final CurrencyLoader loader = new CurrencyLoader(logger);
        final List<CurrencyDefinition> candidates = loader.loadAll(currenciesDir);

        final CurrencyValidator validator = new CurrencyValidator(logger);
        final List<CurrencyDefinition> accepted = validator.validate(candidates);

        this.registry = new CurrencyRegistry();
        for (final CurrencyDefinition definition : accepted) {
            registry.register(definition);
        }
        logger.info("[Economy] Loaded " + registry.size() + " currencies.");

        final StorageRegistry storageRegistry = context.services().resolve(StorageRegistry.class);
        final CoreConfig coreConfig = context.services().resolve(CoreConfig.class);
        this.backend = EconomyBackendFactory.create(coreConfig, dataFolder, storageRegistry, logger);
        logger.info("[Economy] Using storage backend: " + backend.name());

        this.balanceStore = new BalanceStore(backend, logger);
        this.ledgerStore = new LedgerStore(backend, logger);
        this.paymentToggleStore = new PaymentToggleStore(backend);

        final Map<CurrencyType, CurrencyProvider> providers = new EnumMap<>(CurrencyType.class);
        providers.put(CurrencyType.VIRTUAL, new VirtualCurrencyProvider(balanceStore));
        providers.put(CurrencyType.MONEY, new MoneyCurrencyProvider(balanceStore));
        providers.put(CurrencyType.EXP, new ExperienceCurrencyProvider());

        final TaskOrchestrator orchestrator = context.services().resolve(TaskOrchestrator.class);

        final Map<CurrencyType, LeaderboardStrategy> strategies = new EnumMap<>(CurrencyType.class);
        final BalanceBackedLeaderboardStrategy balanceStrategy = new BalanceBackedLeaderboardStrategy(balanceStore);
        strategies.put(CurrencyType.VIRTUAL, balanceStrategy);
        strategies.put(CurrencyType.MONEY, balanceStrategy);

        this.leaderboardService = new LeaderboardService(strategies, backend, orchestrator, config.maxEntries());
        leaderboardService.loadAllFromDisk(registry.all());

        final WithdrawNoteService withdrawNoteService = new WithdrawNoteService(logger);

        this.economyService = new DefaultEconomyService(registry, providers, ledgerStore, paymentToggleStore, leaderboardService, withdrawNoteService);

        final EconomyMessageDispatcher messageDispatcher = new EconomyMessageDispatcher();
        final MenuService menuService = context.services().resolve(MenuService.class);
        final LeaderboardMenuRenderer menuRenderer = new LeaderboardMenuRenderer(menuService);

        final CommandService commandService = context.services().resolve(CommandService.class);
        this.commandRegistrar = new CurrencyCommandRegistrar(economyService, commandService, messageDispatcher, menuRenderer, orchestrator, logger);

        this.placeholders = new EconomyPlaceholders(economyService, registry);
        this.withdrawListener = new WithdrawNoteListener(economyService, registry, messageDispatcher);
    }

    @Override
    protected void onEnable(final ModuleContext context) {
        for (final CurrencyDefinition definition : registry.all()) {
            commandRegistrar.register(definition);
        }

        final PlaceholderService placeholderService = context.services().resolve(PlaceholderService.class);
        placeholders.register(placeholderService);

        context.capabilities().register(CapabilityKey.of("economy", "economy-service", EconomyService.class), economyService);

        this.listenerHost = context.services().resolve(BukkitListenerHost.class);
        listenerHost.register(withdrawListener);

        final JavaPlugin hostPlugin = findHostPlugin();
        if (hostPlugin != null) {
            tryRegisterVault(hostPlugin);
        } else {
            logger.warning("[Economy] Could not locate PrisonCore plugin to register Vault provider.");
        }

        final TaskOrchestrator orchestrator = context.services().resolve(TaskOrchestrator.class);
        scheduleFlushTask(orchestrator);
        scheduleAutoRecalc(orchestrator);

        if (config.rebuildOnEnable()) {
            leaderboardService.rebuildAll(registry.all());
            logger.info("[Economy] Leaderboard rebuild-on-enable triggered for " + registry.size() + " currencies.");
        }

        logger.info("[Economy] Module enabled with " + registry.size() + " currencies.");
    }

    private void scheduleFlushTask(final TaskOrchestrator orchestrator) {
        final long interval = Math.max(250L, config.flushIntervalMillis());
        final Duration period = Duration.ofMillis(interval);
        final TaskSpec spec = TaskSpec.builder(this::runFlush)
                .delay(period)
                .period(period)
                .build();
        this.flushTask = orchestrator.io(spec);
        logger.info("[Economy] Write-behind flush scheduled every " + interval + " ms.");
    }

    private void scheduleAutoRecalc(final TaskOrchestrator orchestrator) {
        final long interval = config.recalcIntervalSeconds();
        if (interval <= 0) {
            logger.info("[Economy] Auto-recalc disabled.");
            return;
        }

        final Duration period = Duration.ofSeconds(interval);
        final TaskSpec spec = TaskSpec.builder(this::runAutoRecalc)
                .delay(period)
                .period(period)
                .build();
        this.recalcTask = orchestrator.io(spec);
        logger.info("[Economy] Auto-recalc scheduled every " + interval + " seconds.");
    }

    private void runFlush() {
        try {
            balanceStore.flush();
            ledgerStore.flush();
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] Flush failed", ex);
        }
    }

    private void runAutoRecalc() {
        for (final CurrencyDefinition definition : registry.all()) {
            try {
                economyService.recalculateLeaderboard(definition.key());
            } catch (final Exception ex) {
                logger.log(Level.WARNING, "[Economy] Auto-recalc failed for " + definition.key().value(), ex);
            }
        }
    }

    @Override
    protected void onDisable(final ModuleContext context) {
        if (recalcTask != null) {
            recalcTask.cancel();
            recalcTask = null;
        }

        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }

        if (commandRegistrar != null) {
            commandRegistrar.unregisterAll();
        }

        if (placeholders != null) {
            final PlaceholderService placeholderService = context.services().resolve(PlaceholderService.class);
            placeholders.unregister(placeholderService);
        }

        if (listenerHost != null && withdrawListener != null) {
            listenerHost.unregister(withdrawListener);
        }

        if (vaultProvider != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultProvider);
        }

        if (balanceStore != null) {
            balanceStore.shutdownFlush();
        }

        if (ledgerStore != null) {
            ledgerStore.shutdownFlush();
        }

        if (backend != null) {
            try {
                backend.close();
            } catch (final Exception ex) {
                logger.log(Level.WARNING, "[Economy] Failed to close backend", ex);
            }
        }

        if (registry != null) {
            registry.clear();
        }
        logger.info("[Economy] Module disabled.");
    }

    private void copyBundledResourceIfMissing(final String resourcePath, final Path target) {
        if (Files.exists(target)) {
            return;
        }
        try (final InputStream stream = EconomyModule.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                logger.warning("[Economy] Bundled resource missing: " + resourcePath);
                return;
            }
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("[Economy] Copied default file: " + target.getFileName());
        } catch (final IOException e) {
            logger.log(Level.WARNING, "[Economy] Failed to copy bundled " + resourcePath, e);
        }
    }

    private JavaPlugin findHostPlugin() {
        return (JavaPlugin) Bukkit.getPluginManager().getPlugin("PrisonCore");
    }

    private void tryRegisterVault(final JavaPlugin hostPlugin) {
        if (!config.vaultEnabled()) {
            logger.info("[Economy] Vault bridge disabled in config.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.info("[Economy] Vault not present — skipping Vault provider registration.");
            return;
        }

        boolean hasMoney = false;
        for (final CurrencyDefinition definition : registry.all()) {
            if (definition.type() == CurrencyType.MONEY) {
                hasMoney = true;
                break;
            }
        }
        if (!hasMoney) {
            logger.info("[Economy] No MONEY currency configured — Vault provider not registered.");
            return;
        }

        this.vaultProvider = new VaultEconomyProvider(economyService);
        Bukkit.getServicesManager().register(
                Economy.class,
                vaultProvider,
                hostPlugin,
                ServicePriority.High
        );
        logger.info("[Economy] Registered Vault economy provider.");
    }
}
