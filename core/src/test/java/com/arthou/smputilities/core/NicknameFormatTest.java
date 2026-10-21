package com.arthou.smputilities.core;

import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.nick.HiddenNamesFormat;
import com.arthou.smputilities.core.nick.NameAnimation;
import com.arthou.smputilities.core.nick.NicknameRules;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NicknameFormatTest {
    private static String legacy(String json) {
        return HiddenNamesFormat.toLegacy(JsonParser.parseString(json));
    }

    @Test
    void hiddenNamesComponentsBecomeColorCodes() {
        assertEquals("Steve", legacy("\"Steve\""));
        assertEquals("&cBob", legacy("{\"text\":\"Bob\",\"color\":\"red\"}"));
        assertEquals("&#ff8800&lSun", legacy("{\"text\":\"Sun\",\"color\":\"#FF8800\",\"bold\":true}"));
        assertEquals("&6Capitão &aJack", legacy("{\"text\":\"\",\"extra\":[{\"text\":\"Capitão \",\"color\":\"gold\"},{\"text\":\"Jack\",\"color\":\"green\"}]}"));
        // Children inherit the parent's style; a child color keeps the formats.
        assertEquals("&9&oSir &e&oX", legacy("{\"text\":\"Sir \",\"color\":\"blue\",\"italic\":true,\"extra\":[{\"text\":\"X\",\"color\":\"yellow\"}]}"));
        // A literal & survives the round trip.
        assertEquals("Tom & Jerry", StyledText.strip(legacy("{\"text\":\"Tom & Jerry\"}")));
        assertEquals("Capitão Jack", StyledText.strip(legacy("{\"text\":\"\",\"extra\":[{\"text\":\"Capitão \",\"color\":\"gold\"},{\"text\":\"Jack\"}]}")));
    }

    @Test
    void animationsAcceptHiddenNamesKeys() {
        assertEquals(NameAnimation.NONE, NameAnimation.parse("hiddennames:null"));
        assertEquals(NameAnimation.RAINBOW, NameAnimation.parse("hiddennames:rainbow"));
        assertEquals(NameAnimation.HIDDEN, NameAnimation.parse("Hidden"));
        assertNull(NameAnimation.parse("sparkle"));
    }

    @Test
    void leadingCodesStyleTheWholeName() {
        assertEquals("&c&l", NicknameRules.leadingCodes("&c&lBob &aX"));
        assertEquals("&#00ff00", NicknameRules.leadingCodes("&#00ff00Green"));
        assertEquals("", NicknameRules.leadingCodes("Plain"));
        assertEquals("", NicknameRules.leadingCodes("&&Amp"));
    }
}
