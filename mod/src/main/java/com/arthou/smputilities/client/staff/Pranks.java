package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.SuButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The staff pranks (TrollBoss' set) by group, for the player actions and
 * the Utilities tab: which take a value first and which need confirming.
 */
final class Pranks {
    enum Group { VISUAL, EFFECTS, MOVEMENT, CHAT, HARM }

    static final Map<Group, List<String>> BY_GROUP = new LinkedHashMap<>();
    /** They hurt, kill or disconnect the player. */
    private static final Set<String> DANGEROUS = Set.of("burn", "bolt", "boom", "hurt", "starve", "spank", "freefall", "anvil", "gokill",
        "trollkick", "crash", "fakerestart");
    static Group group = Group.VISUAL;

    static {
        BY_GROUP.put(Group.VISUAL, List.of("explosion", "lightning", "creeper", "sound", "jumpscare", "abduct", "trample", "squidrain",
            "pumpkinhead", "glow", "title_action", "title_death"));
        BY_GROUP.put(Group.EFFECTS, List.of("nausea", "darkness", "drug", "infect", "freeze", "nomine", "slow", "levitate", "tiny_jump"));
        BY_GROUP.put(Group.MOVEMENT, List.of("launch", "push", "spin", "turn", "randomtp", "border", "switch", "sky", "bury", "trap", "webtrap"));
        BY_GROUP.put(Group.CHAT, List.of("fake_message", "fake_join", "fake_quit", "fakeop", "fakedeop", "spam", "garbage", "trollkick", "crash",
            "fakerestart"));
        BY_GROUP.put(Group.HARM, List.of("burn", "bolt", "boom", "hurt", "starve", "spank", "freefall", "anvil", "gokill"));
    }

    private Pranks() {
    }

    /** A row of chips that picks the group whose buttons are shown. */
    static void chips(StaffPanelScreen panel, int x, int y, int width) {
        Group[] groups = Group.values();
        int chipWidth = (width - 3 * (groups.length - 1)) / groups.length;
        for (int i = 0; i < groups.length; i++) {
            Group value = groups[i];
            panel.add(new SuButton(x + i * (chipWidth + 3), y, chipWidth, 16, PanelTab.tr("troll.group." + value.name().toLowerCase(Locale.ROOT)),
                b -> {
                    group = value;
                    panel.refreshTab();
                }).kind(group == value ? SuButton.Kind.PRIMARY : SuButton.Kind.SUBTLE));
        }
    }

    /** The current group's buttons, made by {@code button}; the dangerous ones in red. */
    static List<SuButton> buttons(Function<String, SuButton> button) {
        List<SuButton> buttons = new ArrayList<>();
        for (String id : BY_GROUP.get(group)) {
            SuButton made = button.apply(id);
            if (DANGEROUS.contains(id)) {
                made.kind(SuButton.Kind.DANGER);
            }
            buttons.add(made);
        }
        return buttons;
    }

    /** Asks for the prank's value or a confirmation when it needs one, then sends it. */
    static void run(StaffPanelScreen panel, UUID target, String id) {
        if (target == null) {
            return;
        }
        switch (id) {
            case "sound" -> panel.form(PanelTab.tr("troll.sound"), List.of(PanelTab.fieldDef("sound", "minecraft:entity.creeper.primed")),
                PanelTab.tr("button.send"), false, null, values -> panel.action("troll", target, id, values.get(0)));
            case "fake_message" -> panel.form(PanelTab.tr("troll.fake_message"), List.of(PanelTab.fieldDef("entity", "Herobrine"),
                PanelTab.fieldDef("message", "")), PanelTab.tr("button.send"), false, null,
                values -> panel.action("troll", target, id, values.get(0).trim() + " " + values.get(1)));
            case "fake_join", "fake_quit" -> panel.form(PanelTab.tr("troll." + id), List.of(PanelTab.fieldDef("entity", "Herobrine")),
                PanelTab.tr("button.send"), false, null, values -> panel.action("troll", target, id, values.get(0)));
            case "title_action" -> panel.form(PanelTab.tr("troll.title_action"), List.of(PanelTab.fieldDef("subtitle", "")), PanelTab.tr("button.send"),
                false, null, values -> panel.action("troll", target, id, values.get(0)));
            case "title_death" -> panel.form(PanelTab.tr("troll.title_death"), List.of(PanelTab.fieldDef("name", ""), PanelTab.fieldDef("color", "red")),
                PanelTab.tr("button.send"), false, null, values -> panel.action("troll", target, id, values.get(0).trim() + " " + values.get(1).trim()));
            case "switch" -> panel.form(PanelTab.tr("troll.switch"), List.of(PanelTab.fieldDef("player", "")), PanelTab.tr("button.send"), false,
                PanelTab.tr("troll.switch.note"), values -> panel.action("troll", target, id, values.get(0).trim()));
            default -> {
                if (DANGEROUS.contains(id)) {
                    panel.confirm(PanelTab.tr("troll." + id), PanelTab.tr("troll.confirm_harm"), true, () -> panel.action("troll", target, id, ""));
                } else {
                    panel.action("troll", target, id, "");
                }
            }
        }
    }
}
