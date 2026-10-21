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
import org.joml.Quaternionf;
//#else
//$$ import com.mojang.math.Matrix4f;
//$$ import com.mojang.math.Vector3f;
//#endif
//#if MC>=1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#else
//$$ import net.minecraft.client.renderer.RenderType;
//#endif

/**
 * Speech bubbles above players with NEXEL's look (the same geometry,
 * textures and opacity as NEXEL and LeafTalk): one bubble per message, the
 * newest with the tail, nearly transparent while the speaker sneaks. Drawn
 * once per frame after the entities, in world space around the camera, which
 * works the same on every loader and version. The bubble turns toward the
 * camera from its own position, so the camera's rotation convention (which
 * changed between versions) does not matter.
 */
public final class BubbleRenderer {
    private static final ResourceLocation ROUNDED_BORDER = SmpUtilities.id("textures/gui/speech_bubbles/rounded_border.png");
    private static final ResourceLocation ROUNDED_FILL = SmpUtilities.id("textures/gui/speech_bubbles/rounded_fill.png");
    private static final ResourceLocation SQUARED_BORDER = SmpUtilities.id("textures/gui/speech_bubbles/squared_border.png");
    private static final ResourceLocation SQUARED_FILL = SmpUtilities.id("textures/gui/speech_bubbles/squared_fill.png");
    private static final float TEXTURE = 32.0F;
    private static final int PADDING = 2;
    private static final int MIN_WIDTH = 13;
    private static final int STACK_GAP = 1;
    private static final int VERTICAL_NUDGE = 13;
    private static final double MAX_DISTANCE_SQR = 64 * 64;
    /** Depth behind the text, in bubble pixels: the fill, then the border. */
    private static final float FILL_DEPTH = 0.5F;
    private static final float BORDER_DEPTH = 1.0F;

    private BubbleRenderer() {
    }

    /**
     * Called by the loader after the entities were drawn.
     *
     * @param poseStack the level's pose stack, camera at the origin
     */
    public static void renderWorld(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui || !BubbleManager.hasAny()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = cameraPosition(camera);
        float partialTick = partialTick(minecraft);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        long now = System.currentTimeMillis();
        boolean drew = false;
        for (Player player : minecraft.level.players()) {
            List<BubbleManager.Bubble> bubbles = BubbleManager.bubbles(player.getUUID());
            boolean typing = BubbleManager.isTyping(player.getUUID(), now);
            if (bubbles.isEmpty() && !typing || !shouldDraw(minecraft, player)) {
                continue;
            }
            double x = Mth.lerp(partialTick, player.xo, player.getX()) - cameraPos.x;
            double y = Mth.lerp(partialTick, player.yo, player.getY()) - cameraPos.y;
            double z = Mth.lerp(partialTick, player.zo, player.getZ()) - cameraPos.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            draw(minecraft, player, bubbles, typing, poseStack, buffers, x, y, z, now);
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
        return camera != null && player.isAlive() && !player.isInvisibleTo(minecraft.player) && camera.distanceToSqr(player) <= MAX_DISTANCE_SQR;
    }

    private static void draw(Minecraft minecraft, Player player, List<BubbleManager.Bubble> bubbles, boolean typing, PoseStack poseStack,
                             MultiBufferSource buffers, double x, double y, double z, long now) {
        Font font = minecraft.font;
        double anchor = player.getBbHeight() + 0.5 + PADDING / 32.0;
        poseStack.pushPose();
        poseStack.translate(0.0, anchor, 0.0);
        face(poseStack, x, y + anchor, z);
        poseStack.translate(0.0F, -VERTICAL_NUDGE, 0.0F);
        float backgroundScale = player.isCrouching() ? 0.2F : 0.9F;

        int offsetY = 0;
        boolean first = true;
        if (typing) {
            BubbleManager.Bubble look = bubbles.isEmpty() ? null : bubbles.get(0);
            String dots = ".".repeat((int) (now / 350L % 3L) + 1);
            Layout layout = new Layout(font.width("..."), 1, font.lineHeight);
            poseStack.pushPose();
            poseStack.translate(0.0F, offsetY, 0.0F);
            Matrix4f matrix = poseStack.last().pose();
            drawBubble(buffers, matrix, layout, look == null ? "rounded" : look.style(), true, argb(look == null ? 0x000000 : look.borderColor(),
                backgroundScale), argb(look == null ? 0xFFFFFF : look.fillColor(), backgroundScale));
            text(font, Texts.literal(dots).getVisualOrderText(), layout.textBaseY, argb(look == null ? 0x000000 : look.textColor(), 1.0F),
                matrix, buffers);
            poseStack.popPose();
            offsetY -= layout.stackHeight + STACK_GAP;
            first = false;
        }
        for (BubbleManager.Bubble bubble : bubbles) {
            float alpha = BubbleManager.alpha(bubble, now);
            if (alpha <= 0.03F) {
                continue;
            }
            List<FormattedCharSequence> lines = font.split(Texts.literal(bubble.text()), Math.max(MIN_WIDTH, bubble.maxWidth()));
            int widest = MIN_WIDTH;
            for (FormattedCharSequence line : lines) {
                widest = Math.max(widest, font.width(line));
            }
            Layout layout = new Layout(Math.min(widest, bubble.maxWidth()), lines.size(), font.lineHeight);
            poseStack.pushPose();
            poseStack.translate(0.0F, offsetY, 0.0F);
            Matrix4f matrix = poseStack.last().pose();
            drawBubble(buffers, matrix, layout, bubble.style(), first, argb(bubble.borderColor(), alpha * backgroundScale),
                argb(bubble.fillColor(), alpha * backgroundScale));
            // The font draws nearly transparent text as opaque, so faded text is skipped.
            if (alpha > 0.1F) {
                int lineY = layout.textBaseY;
                for (FormattedCharSequence line : lines) {
                    text(font, line, lineY, argb(bubble.textColor(), alpha), matrix, buffers);
                    lineY += font.lineHeight;
                }
            }
            poseStack.popPose();
            offsetY -= layout.stackHeight + STACK_GAP;
            first = false;
        }
        poseStack.popPose();
    }

    /**
     * Turns the bubble toward the camera, which looks along (dx, dy, dz), and
     * scales it like a nametag: x to the camera's right, y down, z away.
     */
    private static void face(PoseStack poseStack, double dx, double dy, double dz) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        //#if MC>=1.19.3
        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0.0F));
        //#else
        //$$ poseStack.mulPose(Vector3f.YP.rotationDegrees(-yaw));
        //$$ poseStack.mulPose(Vector3f.XP.rotationDegrees(pitch));
        //#endif
        poseStack.scale(-0.025F, -0.025F, 0.025F);
    }

    private static void text(Font font, FormattedCharSequence line, int y, int color, Matrix4f matrix, MultiBufferSource buffers) {
        float x = -(font.width(line) / 2) + 1;
        //#if MC>=1.19.3
        font.drawInBatch(line, x, y, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        //#else
        //$$ font.drawInBatch(line, x, y, color, false, matrix, buffers, false, 0, LightTexture.FULL_BRIGHT);
        //#endif
    }

    /** NEXEL's bubble geometry for a block of lines; y grows downward, the tail at the bottom. */
    private static final class Layout {
        final int width;
        final int baseX;
        final int baseY;
        final int middleHeight;
        final int textBaseY;
        final int stackHeight;

        Layout(int widestLine, int lineCount, int lineHeight) {
            int balloon = Mth.clamp(widestLine, MIN_WIDTH, 400);
            width = (balloon & 1) == 0 ? balloon - 1 : balloon;
            int count = Math.max(1, lineCount);
            baseX = width / 2;
            baseY = -count - (count - 1) * 8;
            middleHeight = count + (count - 1) * 8 + PADDING * 2;
            textBaseY = -(lineHeight * count - (lineHeight + 1));
            stackHeight = lineHeight * count + PADDING * 2 + 2;
        }
    }

    private static void drawBubble(MultiBufferSource buffers, Matrix4f matrix, Layout layout, String style, boolean tail, int borderColor,
                                   int fillColor) {
        boolean squared = "squared".equalsIgnoreCase(style);
        drawLayer(buffer(buffers, squared ? SQUARED_BORDER : ROUNDED_BORDER), matrix, layout, tail, true, borderColor, BORDER_DEPTH);
        drawLayer(buffer(buffers, squared ? SQUARED_FILL : ROUNDED_FILL), matrix, layout, tail, false, fillColor, FILL_DEPTH);
    }

    /** 5px corners and edges stretched from the 32x32 texture; the border leaves a gap where the tail joins. */
    private static void drawLayer(VertexConsumer consumer, Matrix4f matrix, Layout layout, boolean tail, boolean border, int color, float z) {
        int top = layout.baseY - PADDING;
        int middle = layout.baseY + 5 - PADDING;
        int bottom = 5 + PADDING;
        int left = -layout.baseX - 3 - PADDING;
        int middleX = -layout.baseX + 2 - PADDING;
        int right = layout.baseX - 1 + PADDING;
        int middleWidth = layout.width - 4 + PADDING * 2;

        quad(consumer, matrix, left, top, 5, 5, 0, 0, 5, 5, color, z);
        quad(consumer, matrix, left, middle, 5, layout.middleHeight, 0, 6, 5, 1, color, z);
        quad(consumer, matrix, left, bottom, 5, 5, 0, 8, 5, 5, color, z);
        quad(consumer, matrix, middleX, top, middleWidth, 5, 6, 0, 5, 5, color, z);
        quad(consumer, matrix, middleX, middle, middleWidth, layout.middleHeight, 6, 6, 5, 1, color, z);
        if (tail && border) {
            int leftWidth = (middleWidth - 5) / 2;
            quad(consumer, matrix, middleX, bottom, leftWidth, 5, 6, 8, 5, 5, color, z);
            quad(consumer, matrix, middleX + leftWidth + 5, bottom, middleWidth - leftWidth - 5, 5, 6, 8, 5, 5, color, z);
        } else {
            quad(consumer, matrix, middleX, bottom, middleWidth, 5, 6, 8, 5, 5, color, z);
        }
        quad(consumer, matrix, right, top, 5, 5, 12, 0, 5, 5, color, z);
        quad(consumer, matrix, right, middle, 5, layout.middleHeight, 12, 6, 5, 1, color, z);
        quad(consumer, matrix, right, bottom, 5, 5, 12, 8, 5, 5, color, z);
        if (tail) {
            quad(consumer, matrix, -3, 9 + PADDING, 7, 4, 18, 6, 7, 4, color, z);
        }
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
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    private static VertexConsumer buffer(MultiBufferSource buffers, ResourceLocation texture) {
        //#if MC>=1.21.11
        return buffers.getBuffer(RenderTypes.text(texture));
        //#else
        //$$ return buffers.getBuffer(RenderType.text(texture));
        //#endif
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height,
                             float u, float v, float uWidth, float vHeight, int color, float z) {
        if (width <= 0 || height <= 0) {
            return;
        }
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
