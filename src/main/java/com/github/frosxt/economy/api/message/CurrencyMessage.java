package com.github.frosxt.economy.api.message;

import java.util.List;

/**
 * Multi-channel message definition. Each channel (chat, action bar, title, sound)
 * can be independently enabled or disabled — the dispatcher delivers every enabled
 * channel to players, and only the chat channel to the console.
 */
public record CurrencyMessage(
        String key,
        ChatChannel chat,
        ActionBarChannel actionBar,
        TitleChannel title,
        SoundChannel sound
) {

    public record ChatChannel(boolean enabled, List<String> lines) {

        public ChatChannel {
            lines = List.copyOf(lines);
        }

        public static ChatChannel disabled() {
            return new ChatChannel(false, List.of());
        }
    }

    public record ActionBarChannel(boolean enabled, String value) {

        public static ActionBarChannel disabled() {
            return new ActionBarChannel(false, "");
        }
    }

    public record TitleChannel(
            boolean enabled,
            String title,
            String subtitle,
            int fadeIn,
            int stay,
            int fadeOut
    ) {

        public static TitleChannel disabled() {
            return new TitleChannel(false, "", "", 10, 40, 10);
        }
    }

    public record SoundChannel(boolean enabled, String value, float volume, float pitch) {

        public static SoundChannel disabled() {
            return new SoundChannel(false, "", 1.0f, 1.0f);
        }
    }
}
