package lol.sylvie.sswaystones.storage;

import lol.sylvie.sswaystones.util.HashUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.UUID;

public final class WaystoneRecord {
    private UUID owner;
    private String ownerName;
    private String waystoneName;
    private final BlockPos pos; // Must be final as the hash is calculated based on pos and world
    private final RegistryKey<World> world;
    private boolean global;

    public WaystoneRecord(UUID owner, String ownerName, String waystoneName, BlockPos pos, RegistryKey<World> world, boolean global) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.setWaystoneName(waystoneName); // Limits waystone name
        this.pos = pos;
        this.world = world;
        this.global = global;
    }

    public String asString() {
        return HashUtil.waystoneIdentifier(pos, world);
    }

    public String getHash() {
        return HashUtil.getHash(this);
    }

    public NbtCompound toNbt() {
        NbtCompound waystoneTag = new NbtCompound();

        waystoneTag.putUuid("waystone_owner", owner);
        waystoneTag.putString("waystone_owner_name", ownerName);

        waystoneTag.putString("waystone_name", waystoneName);

        waystoneTag.putIntArray("position", Arrays.asList(pos.getX(), pos.getY(), pos.getZ()));
        waystoneTag.putString("world", world.getValue().toString());

        waystoneTag.putBoolean("global", global);

        return waystoneTag;
    }

    public static WaystoneRecord fromNbt(NbtCompound nbt) {
        UUID waystoneOwner = nbt.getUuid("waystone_owner");
        String waystoneOwnerName = nbt.getString("waystone_owner_name");

        String waystoneName = nbt.getString("waystone_name");

        int[] posAsInt = nbt.getIntArray("position");
        BlockPos position = new BlockPos(posAsInt[0], posAsInt[1], posAsInt[2]);

        Identifier worldIdentifier = Identifier.of(nbt.getString("world"));
        RegistryKey<World> worldRegistryKey = RegistryKey.of(RegistryKeys.WORLD, worldIdentifier);

        boolean global = nbt.getBoolean("global");

        return new WaystoneRecord(waystoneOwner, waystoneOwnerName, waystoneName, position, worldRegistryKey, global);
    }

    public void handleTeleport(ServerPlayerEntity player) {
        BlockPos target = this.getPos();
        assert player.getServer() != null;
        ServerWorld targetWorld = player.getServer().getWorld(this.getWorldKey());

        if (targetWorld == null) {
            player.sendMessage(Text.translatable("error.sswaystones.no_dimension"));
            return;
        }

        // Search for suitable teleport location.
        // It looked weird starting on a corner so I make it try a cardinal direction first
        if (targetWorld.getBlockState(target.add(-1, -1, 0)).isAir()) {
            boolean foundTarget = false;
            searchloop:
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockState state = targetWorld.getBlockState(target.add(x, -1, z));
                    if (!state.isAir()) {
                        target = target.add(x, 0, z);
                        foundTarget = true;
                        break searchloop;
                    }
                }
            }

            if (!foundTarget) {
                target = target.add(0, 2, 0);
            }
        } else {
            target = target.add(-1, 0, 0);
        }


        // Teleport!
        Vec3d center = target.toBottomCenterPos();
        player.teleport(targetWorld, center.getX(), center.getY(), center.getZ(), player.getYaw(), player.getPitch());
        targetWorld.playSound(null, target, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        targetWorld.spawnParticles(ParticleTypes.DRAGON_BREATH, center.getX(), center.getY() + 1f, center.getZ(), 16, 0.5d, 0.5d, 0.5d, 0.1d);
    }

    public UUID getOwnerUUID() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getWaystoneName() {
        return waystoneName;
    }

    public BlockPos getPos() {
        return pos;
    }

    public RegistryKey<World> getWorldKey() {
        return world;
    }

    public boolean isGlobal() {
        return global;
    }

    public void setOwner(PlayerEntity player) {
        this.owner = player.getUuid();
        this.ownerName = player.getGameProfile().getName();
    }

    public void setWaystoneName(String waystoneName) {
        waystoneName = waystoneName.substring(0, Math.min(waystoneName.length(), 32));
        this.waystoneName = waystoneName;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public boolean canEdit(ServerPlayerEntity player) {
        return this.getOwnerUUID().equals(player.getUuid()) || player.hasPermissionLevel(4);
    }
}
