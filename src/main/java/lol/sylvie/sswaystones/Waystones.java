/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones;

import java.util.HashMap;
import java.util.UUID;
import lol.sylvie.sswaystones.block.ModBlocks;
import lol.sylvie.sswaystones.command.WaystonesCommand;
import lol.sylvie.sswaystones.config.Configuration;
import lol.sylvie.sswaystones.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Waystones implements ModInitializer {
    public static String MOD_ID = "sswaystones";
    public static Logger LOGGER = LoggerFactory.getLogger("Server-Side Waystones");
    public static Configuration configuration;

    protected static HashMap<UUID, Long> combatTimestamps = new HashMap<>();

    @Override
    public void onInitialize() {
        configuration = new Configuration(MOD_ID + ".json");
        configuration.load();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> configuration.save()));

        LOGGER.info("{} is made with <3 by sylvie", MOD_ID);
        ModBlocks.initialize();
        ModItems.initialize();

        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> WaystonesCommand.register(dispatcher));

        AttackEntityCallback.EVENT.register((playerEntity, world, hand, entity, entityHitResult) -> {
            if (entity instanceof ServerPlayerEntity otherPlayer)
                combatTimestamps.put(otherPlayer.getUuid(), System.currentTimeMillis());
            return ActionResult.PASS;
        });
    }

    public static Identifier id(String name) {
        return Identifier.of(MOD_ID, name);
    }

    public static boolean isInCombat(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!combatTimestamps.containsKey(uuid))
            return false;
        return combatTimestamps.get(uuid) + (configuration.getInstance().combatCooldown * 1000L) > System
                .currentTimeMillis();
    }
}
