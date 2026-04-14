package com.github.frosxt.economy.withdraw.listener;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRedeemResult;
import com.github.frosxt.economy.currency.CurrencyRegistry;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class WithdrawNoteListener implements Listener {
    private final EconomyService economy;
    private final CurrencyRegistry registry;
    private final EconomyMessageDispatcher messages;

    public WithdrawNoteListener(final EconomyService economy, final CurrencyRegistry registry, final EconomyMessageDispatcher messages) {
        this.economy = economy;
        this.registry = registry;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClick(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        final Player player = event.getPlayer();
        final WithdrawNoteRedeemResult result = economy.redeemWithdrawNote(player.getUniqueId(), item);

        if (result instanceof WithdrawNoteRedeemResult.NotAWithdrawNote) {
            return;
        }

        event.setCancelled(true);

        if (result instanceof final WithdrawNoteRedeemResult.Success success) {
            consumeOneInHand(player);
            registry.find(success.currency()).ifPresent(definition ->
                    definition.messages().find("redeemed-note").ifPresent(message ->
                            messages.send(player, message, Map.of(
                                    "amount", success.amount().toPlainString(),
                                    "currency", definition.displayName()
                            ))));
        }
    }

    private void consumeOneInHand(final Player player) {
        final ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
            player.getInventory().setItemInMainHand(inHand);
        }
    }
}
