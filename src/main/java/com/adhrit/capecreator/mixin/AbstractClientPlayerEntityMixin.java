package com.adhrit.capecreator.mixin;

import com.adhrit.capecreator.CapeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void capecreator$useCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!CapeManager.isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (client.player == null || !self.getUUID().equals(client.player.getUUID())) return;

        PlayerSkin original = cir.getReturnValue();
        ResourceLocation cape = CapeManager.getTextureId();
        cir.setReturnValue(new PlayerSkin(
                original.texture(), original.textureUrl(),
                cape, cape,
                original.model(), original.secure()));
    }
}
