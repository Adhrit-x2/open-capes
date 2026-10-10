package com.adhrit.capecreator.mixin;

import com.adhrit.capecreator.CapeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
//#if MC >= 1.21.9
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//#else
import net.minecraft.client.resources.PlayerSkin;
//#endif
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
        //#if MC >= 1.21.9
        ClientAsset.Texture capeAsset = new ClientAsset.ResourceTexture(cape, cape);
        cir.setReturnValue(new PlayerSkin(original.body(), capeAsset, capeAsset, original.model(), original.secure()));
        //#else
        cir.setReturnValue(new PlayerSkin(
                original.texture(), original.textureUrl(),
                cape, cape,
                original.model(), original.secure()));
        //#endif
    }
}
