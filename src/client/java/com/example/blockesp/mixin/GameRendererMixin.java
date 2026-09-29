package com.example.blockesp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import com.example.blockesp.BlockEspClient;

// Frees our GPU buffer when the game renderer closes.
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "close", at = @At("RETURN"))
	private void blockesp$onClose(CallbackInfo ci) {
		BlockEspClient.close();
	}
}
