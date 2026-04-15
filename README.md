# Economy

A multi-currency economy module for [PrisonCore](https://github.com/frosxt/Prisons). Ships with a money currency that bridges to Vault, an experience currency backed by live Minecraft XP, and two example virtual currencies (tokens and shards) that you can copy into as many custom currencies as you want.

This is a PrisonCore module, not a standalone plugin. You need PrisonCore installed first.

## What it does

For server admins and players, Economy adds:

- Configurable currencies. Each currency lives in its own YAML file under `currencies/`, with its own command, display name, number format, min/max bounds, withdraw note template, and leaderboard menu.
- Per-currency commands. Every currency gets a base command (e.g. `/money`, `/tokens`, `/shards`, `/exp`) with subcommands for balance, give, take, set, pay, top, recalc, withdraw, paytoggle, and help. Each subcommand can be turned off in the currency YAML if you don't want it.
- Player-to-player payments. Players use `/<currency> pay <player> <amount>` to transfer funds. Transfers respect the recipient's pay-toggle setting and the currency's `transferable` rule.
- Pay toggle. Players can opt out of receiving payments per currency with `/<currency> paytoggle`.
- Withdraw notes. Players turn part of their balance into a physical item (`/<currency> withdraw <amount>`) and right-click it later to redeem.Updat
- Leaderboards. `/<currency> top` opens a configurable inventory menu showing the top balances for that currency, with placeholder support in titles, item names, and lore.
- Automatic leaderboard recalculation. The module recomputes leaderboards on a configurable interval (default every 5 minutes) so admins don't have to run `recalc` manually.
- Vault bridge. The module's MONEY currency is registered as a Vault economy provider, so any plugin that reads balances through Vault (shops, jobs, chestshops) sees and updates the same balance the rest of the platform uses. Vault integration can be turned off in `config.yml`.
- Multiple storage backends. The module persists balances, ledger entries, payment toggles, and leaderboard snapshots through whichever backend PrisonCore is configured to use in `core.yml`. JSON, SQLite, MySQL/MariaDB, and MongoDB are all supported.
- Write-behind persistence. Transactions update an in-memory cache immediately and a background flush task writes them to the backend on a configurable interval. Players never wait on disk during a transaction. A clean server stop drains the queue synchronously, so balances are not lost.
- Audit ledger. Every successful transaction writes a structured ledger entry through the same backend, so you can reconstruct who-did-what after the fact.

The four bundled currencies you get out of the box:

- `money.yml` (Coins) — the MONEY currency. Decimals enabled, full command set, bridged to Vault, persistent.
- `exp.yml` (Experience) — bridged directly to Bukkit's `Player.getTotalExperience()`. Whole numbers only. Online players only. The `top`, `recalc`, `pay`, `withdraw`, and `paytoggle` subcommands are off by default because they don't make sense for live XP.
- `tokens.yml` (Tokens) — example virtual currency. Whole numbers, full command set, persistent.
- `shards.yml` (Shards) — second example virtual currency in a different visual theme.

To add a custom currency, copy `tokens.yml` to `currencies/<your-key>.yml`, change the `display-name`, `singular-name`, `plural-name`, and `command.base-command`, and restart. The currency key is the filename minus the `.yml`.

## Requirements

- Java 17
- Spigot or Paper 1.20.1 or later
- PrisonCore installed and running

## Installing

1. Build or download the Economy jar.
2. Drop it into `plugins/PrisonCore/modules/`.
3. Restart the server.

The module copies its default `config.yml` and the four bundled currency files into `plugins/PrisonCore/modules/economy/` on first boot. Edit them in place and restart to reload.

If you want to register the module with Vault, install Vault and leave `vault.enabled` set to `true` in `config.yml` (the default). The module's MONEY currency will be exposed through Vault automatically.

## Configuration

Module-wide settings live in `plugins/PrisonCore/modules/economy/config.yml`:

```yaml
leaderboard:
  rebuild-on-enable: true
  max-entries: 100
  recalc-interval-seconds: 300

storage:
  flush-interval-millis: 2000

vault:
  enabled: true
```

`leaderboard.rebuild-on-enable` triggers a full recalculation when the module enables. `max-entries` caps the number of entries tracked per currency. `recalc-interval-seconds` controls the background recalculation cadence; set it to `0` to disable auto-recalc.

`storage.flush-interval-millis` is the write-behind cadence in milliseconds. Lower values write more frequently, higher values coalesce more aggressively.

`vault.enabled` toggles the Vault economy bridge.

The storage backend itself (JSON, SQLite, SQL, Mongo) is selected globally in PrisonCore's `core.yml`, not here. The Economy module uses whatever backend the platform is using.

## Placeholders

When PlaceholderAPI or PrisonCore's placeholder service is available, Economy registers a resolver under the `economy` namespace. Use these in any platform message, hologram, or scoreboard:

- `%economy_<currency>_balance%` — the player's raw balance, no formatting (e.g. `103039284576378`).
- `%economy_<currency>_formatted_balance%` — the balance formatted with the currency's configured `number-format` policy. If `suffixes-enabled: true`, this is the short suffix form; otherwise it's the comma-grouped form.
- `%economy_<currency>_short_balance%` — the balance forced into short-suffix form (e.g. `1.2k`, `4.7m`, `8.5b`, `3t`, `12q`), regardless of the currency's policy.
- `%economy_<currency>_long_balance%` — the balance forced into comma-grouped form (e.g. `103,039,284,576,378`), regardless of the currency's policy.
- `%economy_<currency>_rank%` — the player's rank in the currency's leaderboard, or `-` if they aren't on it.
- `%economy_<currency>_paytoggle%` — `enabled` or `disabled` based on the player's pay-toggle state.
- `%economy_<currency>_top_<n>_name%` — the username at rank `n` in the leaderboard.
- `%economy_<currency>_top_<n>_amount%` — the raw balance at rank `n`.
- `%economy_<currency>_top_<n>_amount_formatted%` — the rank-`n` balance formatted with the currency's `number-format` policy.
- `%economy_<currency>_top_<n>_amount_short%` — the rank-`n` balance in short-suffix form.
- `%economy_<currency>_top_<n>_amount_long%` — the rank-`n` balance in comma-grouped form.

`<currency>` is the currency key (filename minus `.yml`). For example, `%economy_money_balance%` resolves to the player's coin balance.

### Number formatting

The short-suffix form uses these symbols, escalating by powers of 1,000:

| Symbol | Power | Name |
|---|---|---|
| `k` | 10^3 | thousand |
| `m` | 10^6 | million |
| `b` | 10^9 | billion |
| `t` | 10^12 | trillion |
| `q` | 10^15 | quadrillion |
| `Q` | 10^18 | quintillion |
| `s` | 10^21 | sextillion |
| `S` | 10^24 | septillion |
| `o` | 10^27 | octillion |
| `n` | 10^30 | nonillion |
| `d` | 10^33 | decillion |

Quintillion and larger use uppercase to disambiguate from quadrillion/sextillion/etc. Anything under 1,000 falls back to the grouped form so a player with `847` coins sees `847`, not `0.84k`. Trailing zeros are stripped, so `1500` becomes `1.5k` rather than `1.50k`. Rounding follows the currency's `round-mode` (default `DOWN`).

The grouped form uses comma separators and the currency's configured fractional scale. For Coins (which has `scale: 2` and `allow-decimals: true`), the grouped form of `103039284576378` is `103,039,284,576,378.00`. For Tokens (`scale: 0`, no decimals), it's `103,039,284,576,378`.

To pick a format style globally for a currency, set `number-format.suffixes-enabled` in that currency's YAML. The `formatted_balance` placeholder and the in-game balance command will both honour it. The `short_balance` and `long_balance` placeholders ignore the policy and always use their named style.

## Using the Economy API from another module

Economy publishes its `EconomyService` as a capability so other modules can find it without compile-time coupling. You depend on Economy's api artifacts at compile time, then resolve the service at runtime through PrisonCore's capability registry.

### Add the dependency

Economy is published through [JitPack](https://jitpack.io). Replace `<version>` with a Git tag, branch name, or commit hash from this repo.

#### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.frosxt:Economy:<version>")
}
```

#### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.frosxt:Economy:<version>'
}
```

#### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.frosxt</groupId>
        <artifactId>Economy</artifactId>
        <version>VERSION</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

Always use `compileOnly` (Gradle) or `provided` (Maven). Economy is loaded at runtime by PrisonCore's kernel; shading it into your jar would duplicate every class and break the isolated module classloader.

### Resolve the service

In your module's `onEnable`, look up the capability:

```java
import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.prisoncore.api.capability.CapabilityKey;

private static final CapabilityKey<EconomyService> ECONOMY_SERVICE =
        CapabilityKey.of("economy", "economy-service", EconomyService.class);

@Override
protected void onEnable(final ModuleContext context) {
    final EconomyService economy = context.capabilities().resolve(ECONOMY_SERVICE);
    // ... use it
}
```

If you want your module to work with or without Economy installed, declare `economy` as an `optionalDependency` on your `@ModuleDefinition` and use `resolveOptional`:

```java
final Optional<EconomyService> economy = context.capabilities().resolveOptional(ECONOMY_SERVICE);
```

### EconomyService methods

The full interface is in `com.github.frosxt.economy.api.EconomyService`. The methods you'll actually use:

```java
Optional<CurrencyDefinition> currency(CurrencyKey key);
Collection<CurrencyDefinition> currencies();

BalanceSnapshot balance(UUID playerId, CurrencyKey key);
boolean has(UUID playerId, CurrencyKey key, BigDecimal amount);

TransactionResult deposit(TransactionRequest request);
TransactionResult withdraw(TransactionRequest request);
TransactionResult set(SetBalanceRequest request);
TransactionResult transfer(TransferRequest request);

PaymentToggleState paymentToggle(UUID playerId, CurrencyKey key);
void setPaymentToggle(UUID playerId, CurrencyKey key, boolean enabled);

LeaderboardSnapshot leaderboard(CurrencyKey key);
CompletableFuture<LeaderboardSnapshot> recalculateLeaderboard(CurrencyKey key);

WithdrawNoteIssueResult issueWithdrawNote(WithdrawNoteRequest request);
WithdrawNoteRedeemResult redeemWithdrawNote(UUID playerId, ItemStack item);
```

A few notes on each group:

`currency(key)` and `currencies()` give you the loaded `CurrencyDefinition` records. Use these to discover what currencies the server has configured rather than hardcoding keys. The `CurrencyKey` constructor validates the key against `[a-z0-9][a-z0-9_-]{0,63}`, so you can construct a key from arbitrary input safely as long as you catch `IllegalArgumentException`.

`balance(playerId, key)` always returns a `BalanceSnapshot`, never null. The amount is the player's current balance from the in-memory cache (which is the source of truth, not stale persisted state). This is safe to call from the main thread and is fast (no disk hit).

`has(playerId, key, amount)` is a convenience wrapper that returns `true` if the player's balance is at least `amount`.

`deposit`, `withdraw`, `set`, and `transfer` all return a `TransactionResult` sealed interface. Pattern-match on the variants:

```java
final TransactionResult result = economy.deposit(new TransactionRequest(
        playerId, CurrencyKey.of("money"), BigDecimal.valueOf(100), "quest-reward"));

switch (result) {
    case TransactionResult.Success success ->
        player.sendMessage("Granted: " + success.newBalance().amount());
    case TransactionResult.InsufficientFunds insufficient ->
        player.sendMessage("Not enough");
    case TransactionResult.ExceedsMax exceedsMax ->
        player.sendMessage("Balance cap reached");
    case TransactionResult.NotTransferable notTransferable ->
        player.sendMessage("This currency cannot be transferred");
    case TransactionResult.PaymentsDisabled paymentsDisabled ->
        player.sendMessage("Recipient has payments disabled");
    case TransactionResult.UnknownCurrency unknown ->
        player.sendMessage("No such currency");
    default -> player.sendMessage("Transaction failed");
}
```

The `reason` string on each request flows through to the audit ledger, so use it to record why the transaction happened (`"shop-purchase"`, `"quest-reward"`, `"admin-give"`).

`transfer` is atomic. The economy service acquires per-player locks in canonical UUID order to avoid deadlocks and rolls the debit back if the credit fails, so you don't need to wrap it in your own try/catch for partial-failure recovery.

`paymentToggle(playerId, key)` returns the current toggle state. Absence of a record means "enabled" — pay toggles are opt-out by default. `setPaymentToggle` writes a new value.

`leaderboard(key)` returns the latest cached `LeaderboardSnapshot` synchronously. The snapshot is read-only and may be slightly stale (up to `recalc-interval-seconds` old). For real-time freshness, call `recalculateLeaderboard(key)` and use the returned `CompletableFuture`. The recalculation runs on the platform's IO executor, so the future completes off the main thread; if you want to deliver the result back to a player, hop to the main thread first via `TaskOrchestrator.switchToMainThread()`.

`issueWithdrawNote(request)` debits the player and returns a `WithdrawNoteIssueResult` containing the physical item to hand them. Check for `Success` before consuming the item from the player's inventory.

`redeemWithdrawNote(playerId, item)` is the inverse — it inspects an item, verifies the NBT marker, credits the player, and returns a `WithdrawNoteRedeemResult`. The standard withdraw-note listener that ships with the module already wires this to right-click events, so you only call it directly if you're building your own redemption UI.

### Concurrency model

Reads (`balance`, `has`, `paymentToggle`, `leaderboard`) are safe from any thread. They hit in-memory state under fine-grained locks.

Writes (`deposit`, `withdraw`, `set`, `transfer`, `setPaymentToggle`, `issueWithdrawNote`) are also safe from any thread for VIRTUAL and MONEY currencies. They mutate the in-memory cache and mark dirty entries for the background flush.

The EXP currency is the exception. Its provider operates on live `Player.getTotalExperience()` and must be called from the main thread. The provider throws `IllegalStateException` if you call it off the main thread, so the failure is loud rather than silent.

If you don't know the currency type at the call site, call from the main thread to be safe. You can hop on with `TaskOrchestrator.mainThread(...)` or `switchToMainThread()`.

## Building from source

PrisonCore's platform jars are pulled in from [JitPack](https://jitpack.io), so all you need is the standard Gradle build:

```
./gradlew shadowJar
```

The shaded jar lands in `build/libs/`. Drop it into `plugins/PrisonCore/modules/`.

## Opening issues

If you find a bug, want a feature, or spot something wrong in this README, open an issue on the [GitHub tracker](https://github.com/frosxt/Economy/issues). A few things that make issues easier to act on:

- Pick a title that describes the symptom, not the guessed cause. "Balance resets after restart" beats "BalanceStore bug."
- Include your PrisonCore version, Economy version, server software (Spigot/Paper), and the storage backend you're using (JSON, SQLite, SQL, Mongo).
- If you can reproduce the problem, list the steps. Three bullet points are better than a paragraph.
- Paste relevant log lines inside a fenced code block. Attach the full log as a gist or file if it's longer than a screen.
- For crashes, include the stack trace. The full one, not just the top line.
- For balance corruption or transaction issues, include the relevant ledger entries from `data/ledgers/<currency>.jsonl` (JSON backend) or the equivalent table rows (SQL/SQLite/Mongo).

If the issue is specifically with the PrisonCore platform rather than this module, open it on the [PrisonCore tracker](https://github.com/frosxt/Prisons/issues) instead.

## License

See `LICENSE` in the repository root.
