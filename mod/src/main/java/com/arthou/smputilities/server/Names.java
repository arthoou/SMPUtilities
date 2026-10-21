package com.arthou.smputilities.server;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.vanish.VanishService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Names and player lookup. Everything is keyed by UUID; names and nicknames
 * are only used to find the UUID, so renames never break stored data.
 */
public final class Names {
    /** A player found by name, nickname or UUID, online or not. */
    public record Target(UUID id, String name, ServerPlayer online) {
        public boolean isOnline() {
            return online != null;
        }

        public GameProfile profile() {
            return online != null ? online.getGameProfile() : new GameProfile(id, name);
        }
    }

    private Names() {
    }

    public static String realName(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    public static String nickname(UUID id) {
        SuServer context = SuServer.get();
        if (context == null || !SuServer.config().features.nicknames) {
            return "";
        }
        PlayerRecord record = context.players().get(id);
        return record.hasNickname() ? record.nickname : "";
    }

    /** RP nickname (with colors) when set, otherwise the account name. */
    public static MutableComponent display(ServerPlayer player) {
        String nick = nickname(player.getUUID());
        return nick.isEmpty() ? Texts.literal(realName(player)) : Msg.styled(nick);
    }

    public static String plainDisplay(ServerPlayer player) {
        String nick = nickname(player.getUUID());
        return nick.isEmpty() ? realName(player) : StyledText.strip(nick);
    }

    public static String plainDisplay(UUID id, String fallback) {
        String nick = nickname(id);
        return nick.isEmpty() ? fallback : StyledText.strip(nick);
    }

    /** Whether {@code viewer} may see real account names behind nicknames. */
    public static boolean seesRealNames(ServerPlayer viewer) {
        return viewer == null || !SuServer.config().nicknames.hideRealNames || Perms.has(viewer, Perm.STAFF_REALNAMES);
    }

    /**
     * Finds a player by account name, RP nickname, or UUID. Vanished players
     * are only found by viewers allowed to see them.
     */
    public static Optional<Target> resolve(MinecraftServer server, String input, ServerPlayer viewer) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String value = input.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (realName(player).equalsIgnoreCase(value) && visible(viewer, player)) {
                return Optional.of(new Target(player.getUUID(), realName(player), player));
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String nick = nickname(player.getUUID());
            if (!nick.isEmpty() && StyledText.strip(nick).equalsIgnoreCase(value) && visible(viewer, player)) {
                return Optional.of(new Target(player.getUUID(), realName(player), player));
            }
        }
        try {
            UUID id = UUID.fromString(value);
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                return visible(viewer, online) ? Optional.of(new Target(id, realName(online), online)) : Optional.empty();
            }
            return Optional.of(new Target(id, offlineName(server, id).orElse(value), null));
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }

        SuServer context = SuServer.get();
        if (context != null) {
            Optional<UUID> byName = context.players().idByName(lower);
            if (byName.isPresent()) {
                return Optional.of(new Target(byName.get(), context.players().get(byName.get()).lastName, null));
            }
            for (PlayerRecord record : context.players().all()) {
                if (record.hasNickname() && StyledText.strip(record.nickname).equalsIgnoreCase(value)) {
                    return Optional.of(new Target(record.uuid, record.lastName, null));
                }
            }
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(value);
            if (profile.isPresent()) {
                return Optional.of(new Target(profile.get().getId(), profile.get().getName(), null));
            }
        }
        return Optional.empty();
    }

    public static Optional<String> offlineName(MinecraftServer server, UUID id) {
        SuServer context = SuServer.get();
        if (context != null) {
            Optional<PlayerRecord> record = context.players().find(id);
            if (record.isPresent() && !record.get().lastName.isEmpty()) {
                return Optional.of(record.get().lastName);
            }
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            return cache.get(id).map(GameProfile::getName);
        }
        return Optional.empty();
    }

    private static boolean visible(ServerPlayer viewer, ServerPlayer target) {
        return viewer == null || VanishService.canSee(viewer, target);
    }

    /**
     * Suggests online players the source may see. Viewers who may not see real
     * names get nicknames instead of account names.
     */
    public static SuggestionProvider<CommandSourceStack> onlineSuggestions() {
        return (context, builder) -> {
            ServerPlayer viewer = Mc.player(context.getSource());
            List<String> names = new ArrayList<>();
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                if (viewer != null && !VanishService.canSee(viewer, player)) {
                    continue;
                }
                String nick = nickname(player.getUUID());
                if (seesRealNames(viewer) || nick.isEmpty()) {
                    names.add(realName(player));
                } else {
                    names.add(quote(StyledText.strip(nick)));
                }
            }
            return SharedSuggestionProvider.suggest(names, builder);
        };
    }

    /** Online names plus every known offline player, for staff commands. */
    public static SuggestionProvider<CommandSourceStack> knownSuggestions() {
        return (context, builder) -> {
            List<String> names = new ArrayList<>();
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                names.add(realName(player));
            }
            SuServer server = SuServer.get();
            if (server != null) {
                for (PlayerRecord record : server.players().all()) {
                    if (!record.lastName.isEmpty() && !names.contains(record.lastName)) {
                        names.add(record.lastName);
                    }
                }
            }
            return SharedSuggestionProvider.suggest(names, builder);
        };
    }

    public static String quote(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_' || c == '-' || c == '.' || c == '+')) {
                return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return value;
    }
}
