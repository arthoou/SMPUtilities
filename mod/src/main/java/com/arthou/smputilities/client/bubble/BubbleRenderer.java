package com.arthou.smputilities.client.bubble;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.client.ClientConfig;
import com.arthou.smputilities.mc.Texts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
//#if MC>=1.19.3
import org.joml.Matrix4f;
//#else
//$$ import com.mojang.math.Matrix4f;
//#endif
//#if MC>=1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#else
//$$ import net.minecraft.client.renderer.RenderType;
//#endif

/**
 * Speech bubbles above players (from Bubbles4All). Drawn once per frame
 * after the entities, in world space around the camera, which works the
 * same on every loader and version; the 9-slice frame uses the text render
 * type, so it is lit and depth-tested like nametags.
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

    /**
     * Called by the loader after the entities were drawn.
     *
     * @param poseStack the level's pose stack, camera at the origin
     */
    public static void renderWorld(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !BubbleManager.hasAny()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = cameraPosition(camera);
        float partialTick = partialTick(minecraft);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean drew = false;
        for (Player player : minecraft.level.players()) {
            List<BubbleManager.Bubble> bubbles = BubbleManager.bubbles(player.getUUID());
            if (bubbles.isEmpty() || !shouldDraw(minecraft, player)) {
                continue;
            }
            double x = Mth.lerp(partialTick, player.xo, player.getX()) - cameraPos.x;
            double y = Mth.lerp(partialTick, player.yo, player.getY()) - cameraPos.y;
            double z = Mth.lerp(partialTick, player.zo, player.getZ()) - cameraPos.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            draw(minecraft, player, bubbles, poseStack, camera, buffers);
            poseStack.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch();
        }
    }

    private static boolean shouldDraw(Minecraft minecraft, Player player) {
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        if (player == minecraft.player && (!ClientConfig.get().bubbles.showOwn || firstPerson)) {
            return false;
        }
        Entity camera = minecraft.getCameraEntity();
        return camera != null && !player.isInvisibleTo(minecraft.player) && camera.distanceToSqr(player) <= MAX_DISTANCE_SQR;
    }

    private static void draw(Minecraft minecraft, Player player, List<BubbleManager.Bubble> bubbles, PoseStack poseStack, Camera camera,
                             MultiBufferSource buffers) {
        Font font = minecraft.font;
        long now = System.currentTimeMillis();
        poseStack.pushPose();
        // Above the nametag.
        poseStack.translate(0.0, player.getBbHeight() + 0.75, 0.0);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        float offset = 0;
        for (int index = 0; index < bubbles.size(); index++) {
            BubbleManager.Bubble bubble = bubbles.get(index);
            float alpha = BubbleManager.alpha(bubble, now);
            if (alpha <= 0.02F) {
                continue;
            }
            String text = (bubble.tts() ? "♪ " : "") + bubble.text().trim();
            List<FormattedCharSequence> lines = font.split(Texts.literal(text), bubble.maxWidth());
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
                    //#if MC>=1.19.3
                    font.drawInBatch(line, -font.width(line) / 2.0F, lineY, textColor, false, matrix, buffers,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    //#else
                    //$$ font.drawInBatch(line, -font.width(line) / 2.0F, lineY, textColor, false, matrix, buffers,
                    //$$     false, 0, LightTexture.FULL_BRIGHT);
                    //#endif
                    lineY += font.lineHeight;
                }
            }
            offset += height + 3 + (index == 0 ? 5 : 0);
        }
        poseStack.popPose();
    }

    private static Vec3 cameraPosition(Camera camera) {
        //#if MC>=1.21.8
        return camera.position();
        //#else
        //$$ return camera.getPosition();
        //#endif
    }

    private static float partialTick(Minecraft minecraft) {
        //#if MC>=1.21.3
        return minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        //#elif MC>=1.21
        //$$ return minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        //#else
        //$$ return minecraft.getFrameTime();
        //#endif
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(alpha * 255) & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    private static VertexConsumer buffer(MultiBufferSource buffers, ResourceLocation texture) {
        //#if MC>=1.21.11
        return buffers.getBuffer(RenderTypes.text(texture));
        //#else
        //$$ return buffers.getBuffer(RenderType.text(texture));
        //#endif
    }

    /** 5px corners and edges stretched from the 32x32 texture; the tail sits under the newest bubble. */
    private static void drawFrame(MultiBufferSource buffers, Matrix4f matrix, ResourceLocation texture, int x, int y, int width, int height,
                                  boolean tail, int color, float z) {
        VertexConsumer consumer = buffer(buffers, texture);
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
        vertex(consumer, matrix, x, y, z, color, u0, v0);
        vertex(consumer, matrix, x, y + height, z, color, u0, v1);
        vertex(consumer, matrix, x + width, y + height, z, color, u1, v1);
        vertex(consumer, matrix, x + width, y, z, color, u1, v0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color, float u, float v) {
        //#if MC>=1.21
        consumer.addVertex(matrix, x, y, z).setColor(color).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
        //#else
        //$$ consumer.vertex(matrix, x, y, z).color(color).uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
        //#endif
    }
}
