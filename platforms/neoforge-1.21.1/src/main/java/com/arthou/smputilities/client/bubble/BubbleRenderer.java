package com.arthou.smputilities.client.bubble;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.client.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Speech bubbles above players, ported from Bubbles4All to the 1.21 render
 * pipeline: the 9-slice frame is drawn with the text render type, so it is
 * batched and lit like nametags.
 */
public final class BubbleRenderer {
    private static final ResourceLocation ROUNDED_BORDER = SmpUtilities.id("textures/gui/speech_bubbles/rounded_border.png");
    private static final ResourceLocation ROUNDED_FILL = SmpUtilities.id("textures/gui/speech_bubbles/rounded_fill.png");
    private static final ResourceLocation SQUARED_BORDER = SmpUtilities.id("textures/gui/speech_bubbles/squared_border.png");
    private static final ResourceLocation SQUARED_FILL = SmpUtilities.id("textures/gui/speech_bubbles/squared_fill.png");
    private static final float TEXTURE = 32.0F;
    private static final int PADDING = 2;
    private static final int MIN_WIDTH = 13;
    private static final double MAX_DISTANCE_SQR = 64 * 64;

    private BubbleRenderer() {
    }

    public static void render(Player player, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        List<BubbleManager.Bubble> bubbles = BubbleManager.bubbles(player.getUUID());
        if (bubbles.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player && (!ClientConfig.get().bubbles.showOwn || minecraft.options.getCameraType().isFirstPerson())) {
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || player.isInvisibleTo(minecraft.player) || camera.distanceToSqr(player) > MAX_DISTANCE_SQR) {
            return;
        }
        Font font = minecraft.font;
        long now = System.currentTimeMillis();
        boolean nameVisible = player != minecraft.player || !minecraft.options.getCameraType().isFirstPerson();

        poseStack.pushPose();
        // Above the nametag.
        poseStack.translate(0.0, player.getBbHeight() + (nameVisible ? 0.75 : 0.5), 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        float offset = 0;
        for (int index = 0; index < bubbles.size(); index++) {
            BubbleManager.Bubble bubble = bubbles.get(index);
            float alpha = BubbleManager.alpha(bubble, now);
            if (alpha <= 0.02F) {
                continue;
            }
            String text = (bubble.tts() ? "♪ " : "") + bubble.text().trim();
            List<FormattedCharSequence> lines = font.split(Component.literal(text), bubble.maxWidth());
            int widest = MIN_WIDTH;
            for (FormattedCharSequence line : lines) {
                widest = Math.max(widest, font.width(line));
            }
            int width = widest + PADDING * 2 + 4;
            int height = lines.size() * font.lineHeight + PADDING * 2 + 2;
            int left = -width / 2;
            int top = (int) (-offset - height - (index == 0 ? 5 : 0));
            boolean squared = "squared".equalsIgnoreCase(bubble.style());

            Matrix4f matrix = poseStack.last().pose();
            drawFrame(buffers, matrix, squared ? SQUARED_BORDER : ROUNDED_BORDER, left, top, width, height, index == 0,
                argb(bubble.borderColor(), alpha), 0.03F);
            drawFrame(buffers, matrix, squared ? SQUARED_FILL : ROUNDED_FILL, left, top, width, height, index == 0,
                argb(bubble.fillColor(), alpha), 0.015F);
            if (alpha > 0.1F) {
                int textColor = argb(bubble.textColor(), alpha);
                int lineY = top + PADDING + 2;
                for (FormattedCharSequence line : lines) {
                    font.drawInBatch(line, -font.width(line) / 2.0F, lineY, textColor, false, matrix, buffers,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    lineY += font.lineHeight;
                }
            }
            offset += height + 3 + (index == 0 ? 5 : 0);
        }
        poseStack.popPose();
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(alpha * 255) & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    /** 5px corners and edges stretched from the 32x32 texture; the tail sits under the newest bubble. */
    private static void drawFrame(MultiBufferSource buffers, Matrix4f matrix, ResourceLocation texture, int x, int y, int width, int height,
                                  boolean tail, int color, float z) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        int c = 5;
        int innerWidth = Math.max(1, width - c * 2);
        int innerHeight = Math.max(1, height - c * 2);
        // top row
        quad(consumer, matrix, x, y, c, c, 0, 0, 5, 5, color, z);
        quad(consumer, matrix, x + c, y, innerWidth, c, 6, 0, 5, 5, color, z);
        quad(consumer, matrix, x + c + innerWidth, y, c, c, 12, 0, 5, 5, color, z);
        // middle row
        quad(consumer, matrix, x, y + c, c, innerHeight, 0, 6, 5, 1, color, z);
        quad(consumer, matrix, x + c, y + c, innerWidth, innerHeight, 6, 6, 5, 1, color, z);
        quad(consumer, matrix, x + c + innerWidth, y + c, c, innerHeight, 12, 6, 5, 1, color, z);
        // bottom row
        quad(consumer, matrix, x, y + c + innerHeight, c, c, 0, 8, 5, 5, color, z);
        quad(consumer, matrix, x + c, y + c + innerHeight, innerWidth, c, 6, 8, 5, 5, color, z);
        quad(consumer, matrix, x + c + innerWidth, y + c + innerHeight, c, c, 12, 8, 5, 5, color, z);
        if (tail) {
            quad(consumer, matrix, -3, y + height - 1, 7, 4, 18, 6, 7, 4, color, z);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height,
                             float u, float v, float uWidth, float vHeight, int color, float z) {
        float u0 = u / TEXTURE;
        float u1 = (u + uWidth) / TEXTURE;
        float v0 = v / TEXTURE;
        float v1 = (v + vHeight) / TEXTURE;
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(matrix, x, y, z).setColor(color).setUv(u0, v0).setLight(light);
        consumer.addVertex(matrix, x, y + height, z).setColor(color).setUv(u0, v1).setLight(light);
        consumer.addVertex(matrix, x + width, y + height, z).setColor(color).setUv(u1, v1).setLight(light);
        consumer.addVertex(matrix, x + width, y, z).setColor(color).setUv(u1, v0).setLight(light);
    }
}
