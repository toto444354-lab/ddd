package com.majinvegeta;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric 1.21.1 core for Majin Vegeta's self-destruct. */
public final class MajinVegetaMod implements ModInitializer {
    public static final String MOD_ID = "majin_vegeta";
    private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();
    private static final List<Blast> BLASTS = new ArrayList<>();

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(MajinVegetaMod::tick);
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("majin").then(CommandManager.literal("charge")
                .executes(c -> { PlayerEntity p = c.getSource().getPlayer(); startCharge(p); return 1; }))
            .then(CommandManager.literal("release")
                .executes(c -> { PlayerEntity p = c.getSource().getPlayer(); release(p); return 1; }))
            .then(CommandManager.literal("cancel")
                .executes(c -> { PlayerEntity p = c.getSource().getPlayer(); cancel(p); return 1; }))
            .then(CommandManager.literal("dummy").then(CommandManager.literal("spawn")
                .executes(c -> { ServerCommandSource s=c.getSource(); s.getWorld().getServer().getCommandManager().executeWithPrefix(s, "summon armor_stand ~ ~ ~ {CustomName:\"\\\"Majin Dummy\\\"\",Invulnerable:1b,NoGravity:1b}"); return 1; }))
            .then(CommandManager.literal("dummy_charge").executes(c -> { startCharge(c.getSource().getPlayer()); return 1; }))
            .then(CommandManager.literal("dummy_release").executes(c -> { release(c.getSource().getPlayer()); return 1; }))));
    }

    private static void startCharge(PlayerEntity p) { if (p instanceof ServerPlayerEntity sp) CHARGES.putIfAbsent(sp.getUuid(), new Charge(sp)); }
    private static void cancel(PlayerEntity p) { if (p != null) CHARGES.remove(p.getUuid()); }
    private static void release(PlayerEntity p) { if (p instanceof ServerPlayerEntity sp) { Charge c=CHARGES.remove(sp.getUuid()); if(c!=null) BLASTS.add(new Blast(sp, c.power())); } }

    private static void tick(MinecraftServer server) {
        CHARGES.values().removeIf(c -> !c.player.isAlive() || c.player.getWorld().getServer() == null);
        CHARGES.values().forEach(Charge::tick);
        BLASTS.removeIf(b -> !b.tick());
    }

    private static final class Charge {
        final ServerPlayerEntity player; int ticks;
        Charge(ServerPlayerEntity p){player=p;}
        void tick(){ ticks=Math.min(200,ticks+1); ServerWorld w=player.getServerWorld(); Vec3d p=player.getPos();
            float f=power(); int count=Math.max(1,(int)(f*16));
            w.spawnParticles(ParticleTypes.END_ROD,p.x,p.y+1.0,p.z,count,1.2*f,1.2*f,1.2*f,0.02);
            // Ground-based campfire smoke: search downward, never emit from the body while airborne.
            BlockPos ground=player.getBlockPos(); for(int i=0;i<10 && ground.getY()>w.getBottomY() && w.getBlockState(ground).isAir();i++) ground=ground.down();
            if(!w.getBlockState(ground).isAir() && player.getY()-ground.getY()<10) w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,ground.getX()+.5,ground.getY()+1,ground.getZ()+.5,(int)(8+f*40),1.5*f,.15,1.5*f,.01);
        }
        float power(){return Math.min(1f,ticks/200f);}
    }

    private static final class Blast {
        final ServerPlayerEntity owner; final ServerWorld world; final Vec3d center; final float power; final int maxRadius; int age; int clearCursor;
        Blast(ServerPlayerEntity p,float f){owner=p;world=p.getServerWorld();center=p.getPos();power=f;maxRadius=Math.max(8,Math.round(200*f));}
        boolean tick(){ age++; float progress=Math.min(1f,age/80f); int radius=Math.max(2,Math.round(maxRadius*progress));
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,center.x,center.y,center.z,1,0,0,0,0);
            world.spawnParticles(ParticleTypes.END_ROD,center.x,center.y,center.z,Math.min(250,20+radius*2),radius*.4,radius*.25,radius*.4,.08);
            damage(radius); push(radius); clear(radius);
            // Hold the fully expanded blast for 100 ticks while continuing damage.
            return age < 80 + 100 + 30;
        }
        void damage(int radius){ Box box=new Box(center.subtract(radius,radius,radius),center.add(radius,radius,radius));
            for(LivingEntity e:world.getEntitiesByClass(LivingEntity.class,box,x -> x!=owner && x.isAlive())) {
                if(e.squaredDistanceTo(center)<=radius*(double)radius) e.damage(world.getDamageSources().outOfWorld(),Math.max(1f,e.getMaxHealth()*.30f/20f));
            }
        }
        void push(int radius){ Box box=new Box(center.subtract(radius,radius,radius),center.add(radius,radius,radius));
            for(Entity e:world.getOtherEntities(owner,box,x->x instanceof LivingEntity)) { Vec3d d=e.getPos().subtract(center); double len=Math.max(.1,d.length()); e.addVelocity(d.x/len*.08,d.y/len*.025,d.z/len*.08); e.velocityModified=true; }
        }
        void clear(int radius){ int budget=50000; int r=Math.min(radius,maxRadius); int diameter=r*2+1; int total=diameter*diameter*diameter;
            for(int n=0;n<budget && clearCursor<total; n++,clearCursor++){ int i=clearCursor; int x=i%diameter-r; int y=(i/diameter)%diameter-r; int z=i/(diameter*diameter)-r; if(x*x+y*y+z*z>r*r) continue; BlockPos pos=BlockPos.ofFloored(center.x+x,center.y+y,center.z+z); BlockState state=world.getBlockState(pos); if(!state.isAir() && !state.isOf(net.minecraft.block.Blocks.BEDROCK)) world.breakBlock(pos,false,owner); }
            world.getPlayers().forEach(p -> { if(p.squaredDistanceTo(center)<r*r) world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,p.getX(),p.getY(),p.getZ(),10,2,1,2,.02); });
        }
    }
}
