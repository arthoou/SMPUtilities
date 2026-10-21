package com.arthou.smputilities.mixin;

import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.platform.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC>=1.21.6
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//#endif

/** Saves {@link PlayerData} with the player and carries it over to the respawned entity. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDataMixin implements PlayerData {
    @Unique
    private CompoundTag smputilities$data = new CompoundTag();

    @Override
    public CompoundTag smputilities$data() {
        return smputilities$data;
    }

    @Override
    public void smputilities$setData(CompoundTag data) {
        smputilities$data = data == null ? new CompoundTag() : data;
    }

    //#if MC>=1.21.6
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void smputilities$save(ValueOutput output, CallbackInfo ci) {
        if (!smputilities$data.isEmpty()) {
            output.store(KEY, CompoundTag.CODEC, smputilities$data.copy());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void smputilities$load(ValueInput input, CallbackInfo ci) {
        smputilities$data = input.read(KEY, CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }
    //#else
    //$$ @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    //$$ private void smputilities$save(CompoundTag tag, CallbackInfo ci) {
    //$$     if (!smputilities$data.isEmpty()) {
    //$$         tag.put(KEY, smputilities$data.copy());
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //$$ private void smputilities$load(CompoundTag tag, CallbackInfo ci) {
    //$$     smputilities$data = Nbt.getCompound(tag, KEY).copy();
    //$$ }
    //#endif

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void smputilities$copy(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        smputilities$data = ((PlayerData) old).smputilities$data().copy();
    }
}
