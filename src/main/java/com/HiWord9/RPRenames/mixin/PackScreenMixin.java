package com.HiWord9.RPRenames.mixin;

import com.HiWord9.RPRenames.configManager;
import net.minecraft.client.gui.screen.pack.PackScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackScreen.class)
public class PackScreenMixin{

	//close (onClose) method_25419
	@Inject(at = @At("RETURN"), method = "method_25419")
	private void listCreator(CallbackInfo ci) {
		configManager.jsonManage();
	}
}
