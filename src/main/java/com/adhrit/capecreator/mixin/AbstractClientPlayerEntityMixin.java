package com.adhrit.capecreator.mixin;

import com.adhrit.capecreator.CapeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void capecreator$useCustomCape(CallbackInfoReturnable<SkinTextures> cir) {
        if (!CapeManager.isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        if (client.player == null || !self.getUuid().equals(client.player.getUuid())) return;

        SkinTextures original = cir.getReturnValue();
        Identifier cape = CapeManager.getTextureId();
        cir.setReturnValue(new SkinTextures(
                original.texture(), original.textureUrl(),
                cape, cape,
                original.model(), original.secure()));
    }
}
