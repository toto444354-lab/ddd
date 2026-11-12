package com.toto444354.majin;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
public final class MajinVegetaMod implements ModInitializer {
 public static final String MOD_ID="majin_vegeta";
 public void onInitialize() {
  ServerTickEvents.END_SERVER_TICK.register(MajinEffectController.INSTANCE::tick);
  CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("majin")
   .then(CommandManager.literal("charge").executes(c->{PlayerEntity p=c.getSource().getPlayer(); MajinEffectController.INSTANCE.startCharge(p); p.sendMessage(Text.literal("§e魔人貝吉塔：開始蓄力"),true); return 1;}))
   .then(CommandManager.literal("release").executes(c->{MajinEffectController.INSTANCE.release(c.getSource().getPlayer()); return 1;}))
   .then(CommandManager.literal("cancel").executes(c->{MajinEffectController.INSTANCE.cancelCharge(c.getSource().getPlayer()); return 1;}))
   .then(CommandManager.literal("dummy").then(CommandManager.literal("spawn").executes(c->{c.getSource().getServer().getCommandManager().executeWithPrefix(c.getSource(),"summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"Majin Dummy\"}',Invulnerable:1b,NoGravity:1b,Tags:[\"majin_dummy\"]}"); return 1;}))));
 }
}
