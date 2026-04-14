package com.github.frosxt.economy.message;

import com.github.frosxt.economy.api.message.CurrencyMessage;
import com.github.frosxt.prisoncore.commons.bukkit.color.ColorTranslator;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Delivers {@link CurrencyMessage} instances parsed from currency YAML files to
 * Bukkit audiences. Mirrors the platform's BukkitMessageService behavior: players
 * receive every enabled channel, console receives chat only.
 *
 * Placeholder substitution uses {@code %placeholder%} syntax to match the
 * ItemPlaceholder conventions in the Economy currency files.
 */
public final class EconomyMessageDispatcher {

    public void send(final CommandSender sender, final CurrencyMessage message) {
        send(sender, message, Collections.emptyMap());
    }

    public void send(final CommandSender sender, final CurrencyMessage message, final Map<String, String> replacements) {
        if (sender == null || message == null) {
            return;
        }
        if (sender instanceof final Player player) {
            deliverToPlayer(player, message, replacements);
        } else {
            deliverToConsole(sender, message, replacements);
        }
    }

    public void broadcast(final CurrencyMessage message, final Map<String, String> replacements) {
        if (message == null) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            deliverToPlayer(player, message, replacements);
        }
        deliverToConsole(Bukkit.getConsoleSender(), message, replacements);
    }

    private void deliverToPlayer(final Player player, final CurrencyMessage message, final Map<String, String> replacements) {
        final CurrencyMessage.ChatChannel chat = message.chat();
        if (chat.enabled()) {
            for (final String line : chat.lines()) {
                player.sendMessage(ColorTranslator.colorize(apply(line, replacements)));
            }
        }

        final CurrencyMessage.ActionBarChannel actionBar = message.actionBar();
        if (actionBar.enabled() && !actionBar.value().isEmpty()) {
            final String text = ColorTranslator.colorize(apply(actionBar.value(), replacements));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
        }

        final CurrencyMessage.TitleChannel title = message.title();
        if (title.enabled()) {
            player.sendTitle(
                    ColorTranslator.colorize(apply(title.title(), replacements)),
                    ColorTranslator.colorize(apply(title.subtitle(), replacements)),
                    title.fadeIn(),
                    title.stay(),
                    title.fadeOut()
            );
        }

        final CurrencyMessage.SoundChannel sound = message.sound();
        if (sound.enabled() && !sound.value().isEmpty()) {
            playSound(player, sound);
        }
    }

    private void deliverToConsole(final CommandSender sender, final CurrencyMessage message,
                                   final Map<String, String> replacements) {
        final CurrencyMessage.ChatChannel chat = message.chat();
        if (!chat.enabled()) {
            return;
        }
        for (final String line : chat.lines()) {
            sender.sendMessage(ColorTranslator.colorize(apply(line, replacements)));
        }
    }

    private void playSound(final Player player, final CurrencyMessage.SoundChannel sound) {
        try {
            final Sound enumSound = Sound.valueOf(sound.value().toUpperCase(Locale.ROOT).replace('.', '_'));
            player.playSound(player.getLocation(), enumSound, sound.volume(), sound.pitch());
        } catch (final IllegalArgumentException e) {
            player.playSound(player.getLocation(), sound.value(), sound.volume(), sound.pitch());
        }
    }

    private String apply(final String input, final Map<String, String> replacements) {
        if (input == null || input.isEmpty() || replacements.isEmpty()) {
            return input;
        }
        String result = input;
        for (final Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }
}
