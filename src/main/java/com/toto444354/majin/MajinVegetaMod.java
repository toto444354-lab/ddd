
package com.toto444354.majin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class MajinVegetaMod implements ModInitializer {

    public static final String MOD_ID = "majin_vegeta";

    @Override
    public void onInitialize() {

        ServerTickEvents.END_SERVER_TICK.register(
            MajinEffectController.INSTANCE::tick
        );

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> {

                dispatcher.register(
                    CommandManager.literal("majin")

                        .then(CommandManager.literal("charge")
                            .executes(context -> {
                                ServerPlayerEntity player =
                                    context.getSource().getPlayerOrThrow();

                                MajinEffectController.INSTANCE.startCharge(player);

                                player.sendMessage(
                                    Text.literal("§e魔人貝吉塔：開始蓄力"),
                                    true
                                );

                                return 1;
                            })
                        )

                        .then(CommandManager.literal("release")
                            .executes(context -> {
                                MajinEffectController.INSTANCE.release(
                                    context.getSource().getPlayerOrThrow()
                                );
                                return 1;
                            })
                        )

                        .then(CommandManager.literal("cancel")
                            .executes(context -> {
                                MajinEffectController.INSTANCE.cancelCharge(
                                    context.getSource().getPlayerOrThrow()
                                );
                                return 1;
                            })
                        )

                        .then(CommandManager.literal("dummy")
                            .then(CommandManager.literal("spawn")
                                .executes(context -> {

                                    String command =
                                        "summon armor_stand ~ ~ ~ " +
                                        "{CustomName:'{\"text\":\"Majin Dummy\"}'," +
                                        "Invulnerable:1b," +
                                        "NoGravity:1b," +
                                        "Tags:[\"majin_dummy\"]}";

                                    context.getSource()
                                        .getServer()
                                        .getCommandManager()
                                        .executeWithPrefix(
                                            context.getSource(),
                                            command
                                        );

                                    return 1;
                                })
                            )
                        )
                );
            }
        );
    }
}
