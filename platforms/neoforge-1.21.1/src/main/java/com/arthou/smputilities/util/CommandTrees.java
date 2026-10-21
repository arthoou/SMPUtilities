package com.arthou.smputilities.util;

import com.arthou.smputilities.core.util.SuLog;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import java.lang.reflect.Field;
import java.util.Map;

/** Removes existing root commands so SMPutilities can provide its own version (e.g. /msg). */
public final class CommandTrees {
    private static Field children;
    private static Field literals;

    private CommandTrees() {
    }

    public static <S> void remove(CommandDispatcher<S> dispatcher, String... names) {
        try {
            if (children == null) {
                children = CommandNode.class.getDeclaredField("children");
                literals = CommandNode.class.getDeclaredField("literals");
                children.setAccessible(true);
                literals.setAccessible(true);
            }
            CommandNode<S> root = dispatcher.getRoot();
            @SuppressWarnings("unchecked")
            Map<String, ?> childMap = (Map<String, ?>) children.get(root);
            @SuppressWarnings("unchecked")
            Map<String, ?> literalMap = (Map<String, ?>) literals.get(root);
            for (String name : names) {
                childMap.remove(name);
                literalMap.remove(name);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            SuLog.warn("Could not replace vanilla commands " + String.join(", ", names), e);
        }
    }
}
