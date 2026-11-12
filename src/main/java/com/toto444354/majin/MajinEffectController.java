package com.toto444354.majin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side state machine for Majin Vegeta's charge and self-destruct.
 *  Call {@link #tick(MinecraftServer)} once from SERVER_STARTED's tick callback.
 *  All visual effects are server particles, so vanilla clients need no resource pack.
 */
public final class MajinEffectController {
    public static final MajinEffectController INSTANCE = new MajinEffectController();
    private static final int MAX_RADIUS = 200;
    private static final int CHARGE_TICKS = 20 * 10;
    private static final int HOLD_TICKS = 20 * 4;
    private static final int BLOCKS_PER_TICK = 3_000;
    private final Map<UUID, Charge> charges = new HashMap<>();

    private MajinEffectController() {}

    public void startCharge(LivingEntity owner) {
        charges.computeIfAbsent(owner.getUuid(), id -> new Charge(owner));
    }

    public void cancelCharge(LivingEntity owner) {
        Charge c = charges.get(owner.getUuid());
        if (c != null) c.canceling = true;
    }

    public void release(LivingEntity owner) {
        Charge c = charges.get(owner.getUuid());
        if (c != null) { c.released = true; c.canceling = false; c.blastTicks = 0; }
    }

    public boolean isCharging(UUID id) { return charges.containsKey(id); }

    public void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Charge>> it = charges.entrySet().iterator();
        while (it.hasNext()) {
            Charge c = it.next().getValue();
            Entity e = c.owner;
            if (!e.isAlive() || !(e.getWorld() instanceof ServerWorld world)) { it.remove(); continue; }
            c.tick(world);
            if (c.finished) it.remove();
        }
    }

    private static final class Charge {
        final Entity owner;
        final ArrayDeque<BlockPos> blocks = new ArrayDeque<>();
        int age;
        int radius;
        boolean released, canceling, finished;
        int blastTicks = -1;
        Charge(Entity owner) { this.owner = owner; }

        void tick(ServerWorld world) {
            age++;
            if (canceling && !released) {
                spawnCharge(world, Math.max(0, 1.0 - age / 12.0));
                if (age > 12) finished = true;
                return;
            }
            if (!released) {
                double progress = Math.min(1.0, age / (double) CHARGE_TICKS);
                radius = Math.max(2, (int) Math.round(MAX_RADIUS * progress));
                spawnCharge(world, progress);
                pushNearby(world, Math.min(8.0, 0.15 + progress * 0.35));
                return;
            }
            int blastAge = blastTicks >= 0 ? blastTicks++ : Math.max(0, age - CHARGE_TICKS);
            if (blastAge <= 8) radius = Math.max(radius, 2 + blastAge * Math.max(1, radius / 8));
            spawnBlast(world, Math.min(radius, MAX_RADIUS), blastAge);
            damageAndPush(world, Math.min(radius, MAX_RADIUS), blastAge);
            clearBlocks(world, Math.min(radius, MAX_RADIUS));
            if (blastAge > HOLD_TICKS + 30) finished = true;
        }

        private void spawnCharge(ServerWorld world, double progress) {
            Vec3d p = owner.getPos().add(0, owner.getHeight() * .55, 0);
            int rings = Math.max(2, (int) (2 + progress * 7));
            double t = age * .16;
            for (int ring = 0; ring < rings; ring++) {
                double r = 0.8 + ring * .28 + progress * 1.2;
                for (int i = 0; i < 12; i++) {
                    double a = t + i * Math.PI * 2 / 12.0 + ring * .2;
                    world.spawnParticles(ParticleTypes.END_ROD, p.x + Math.cos(a) * r, p.y + Math.sin(a * .7) * r * .45, p.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            // Campfire smoke must originate from terrain below the player.
            BlockPos floor = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, owner.getBlockPos());
            if (floor.getY() <= owner.getBlockY() + 4 && owner.getY() - floor.getY() < 12) {
                int count = Math.max(1, (int) (progress * 8));
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, floor.getX() + .5, floor.getY() + .1, floor.getZ() + .5, count, 1.8 * progress, .15, 1.8 * progress, .015);
            }
            int specks = Math.max(1, (int) (progress * 20));
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, specks, .8 + progress * 2, .8 + progress * 2, .8 + progress * 2, .01);
        }

        private void spawnBlast(ServerWorld world, int r, int blastAge) {
            Vec3d p = owner.getPos().add(0, owner.getHeight() * .55, 0);
            int edge = Math.max(12, r / 3);
            int count = blastAge < 10 ? 100 : 35;
            world.spawnParticles(ParticleTypes.FLASH, p.x, p.y, p.z, blastAge < 3 ? 1 : 0, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, count, r * .35, r * .35, r * .35, .02);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, edge, r * .45, r * .20, r * .45, .03);
            // Outer halo rotates counter-clockwise (negative angle); keep the core clear.
            double spin = -blastAge * .18;
            for (int i = 0; i < 24; i++) {
                double a = spin + i * Math.PI * 2.0 / 24.0;
                double rr = Math.max(2, r * .82);
                world.spawnParticles(ParticleTypes.END_ROD, p.x + Math.cos(a) * rr, p.y + Math.sin(i * .7) * rr * .18, p.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
            if (blastAge > HOLD_TICKS) world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, Math.max(40, r / 2), r * .7, .5, r * .7, .06);
        }

        private void pushNearby(ServerWorld world, double strength) {
            Box box = owner.getBoundingBox().expand(10);
            for (Entity e : world.getOtherEntities(owner, box, x -> x instanceof LivingEntity && x.isAlive())) {
                Vec3d d = e.getPos().subtract(owner.getPos());
                if (d.lengthSquared() > .001) { e.addVelocity(d.normalize().multiply(strength)); e.velocityModified = true; }
            }
        }

        private void damageAndPush(ServerWorld world, int r, int blastAge) {
            if (blastAge % 20 != 0) return;
            Box box = owner.getBoundingBox().expand(r);
            for (Entity e : world.getOtherEntities(owner, box, x -> x instanceof LivingEntity && x.isAlive())) {
                double d = e.distanceTo(owner);
                if (d <= r) {
                    LivingEntity living = (LivingEntity) e;
                    living.setHealth(Math.max(0, living.getHealth() - living.getMaxHealth() * .30f));
                    Vec3d dir = living.getPos().subtract(owner.getPos());
                    if (dir.lengthSquared() > .001) { living.addVelocity(dir.normalize().multiply(.5)); living.velocityModified = true; }
                }
            }
        }

        private void clearBlocks(ServerWorld world, int r) {
            if (blocks.isEmpty()) {
                BlockPos c = owner.getBlockPos();
                int rr = Math.min(r, MAX_RADIUS);
                for (int x = -rr; x <= rr; x += 2) for (int y = -rr; y <= rr; y += 2) for (int z = -rr; z <= rr; z += 2)
                    if (x * x + y * y + z * z <= rr * rr) blocks.add(c.add(x, y, z));
            }
            int n = 0;
            while (!blocks.isEmpty() && n++ < BLOCKS_PER_TICK) {
                BlockPos pos = blocks.poll();
                if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 2);
            }
        }
    }
}
