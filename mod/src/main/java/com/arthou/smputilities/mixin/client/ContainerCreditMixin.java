package com.arthou.smputilities.mixin.client;

import com.arthou.smputilities.client.gui.Credit;
import com.arthou.smputilities.client.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC>=1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif

/** "Made by arthou" on the chest-style menus SMPutilities opens (inventories, backups, profiles). */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerCreditMixin {
    //#if MC>=1.20
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), require = 0)
    private void smputilities$credit(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        Credit.drawForMenu(new Gfx(graphics), Minecraft.getInstance().font, screen.getTitle(), screen.width, screen.height);
    }
    //#else
    //$$ @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V", at = @At("TAIL"), require = 0)
    //$$ private void smputilities$credit(PoseStack pose, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    //$$     Screen screen = (Screen) (Object) this;
    //$$     Credit.drawForMenu(new Gfx(pose, screen), Minecraft.getInstance().font, screen.getTitle(), screen.width, screen.height);
    //$$ }
    //#endif
}
