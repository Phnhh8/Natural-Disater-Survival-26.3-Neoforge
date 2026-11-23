package com.example.disasters;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Random;

@Mod(NaturalDisastersMod.MODID)
public class NaturalDisastersMod {
    public static final String MODID = "naturaldisasters";
    private static int tickCounter = 0, disasterDuration = 0, tsunamiWaveX = 0;
    private static String activeDisaster = "NONE";
    private static Vec3 tornadoPos = null;
    private static BlockPos targetPos = null;
    private static final Random RANDOM = new Random();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<Item> METEOR_CALLER = reg("meteor_caller", "METEOR", "Triệu Hồi Mưa Thiên Thạch");
    public static final DeferredItem<Item> EARTHQUAKE_CALLER = reg("earthquake_caller", "EARTHQUAKE", "Triệu Hồi Động Đất");
    public static final DeferredItem<Item> ACID_RAIN_CALLER = reg("acid_rain_caller", "ACID_RAIN", "Triệu Hồi Mưa Axit");
    public static final DeferredItem<Item> TORNADO_CALLER = reg("tornado_caller", "TORNADO", "Triệu Hồi Lốc Xoáy");
    public static final DeferredItem<Item> LANDSLIDE_CALLER = reg("landslide_caller", "LANDSLIDE", "Triệu Hồi Sạt Lở Đất");
    public static final DeferredItem<Item> NUKE_CALLER = reg("nuke_caller", "NUKE", "Triệu Hồi Bom Hạt Nhân");
    public static final DeferredItem<Item> TSUNAMI_CALLER = reg("tsunami_caller", "TSUNAMI", "Triệu Hồi Sóng Thần");
    public static final DeferredItem<Item> VOLCANO_CALLER = reg("volcano_caller", "VOLCANO", "Triệu Hồi Núi Lửa");
    public static final DeferredItem<Item> SINKHOLE_CALLER = reg("sinkhole_caller", "SINKHOLE", "Triệu Hồi Hố Sụt");
    public static final DeferredItem<Item> LIGHTNING_CALLER = reg("lightning_caller", "LIGHTNING_STORM", "Triệu Hồi Bão Sấm Sét");
    public static final DeferredItem<Item> BLIZZARD_CALLER = reg("blizzard_caller", "BLIZZARD", "Triệu Hồi Bão Tuyết");
    public static final DeferredItem<Item> ZOMBIE_CALLER = reg("zombie_caller", "ZOMBIE_APOCALYPSE", "Triệu Hồi Đại Dịch Zombie");
    public static final DeferredItem<Item> DISASTER_STOPPER = reg("disaster_stopper", "STOP", "Dừng Toàn Bộ Thảm Họa");

    private static DeferredItem<Item> reg(String name, String type, String displayName) {
        return ITEMS.registerItem(name, props -> new DisasterItem(props.stacksTo(1), type, displayName));
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DISASTER_TAB = TABS.register("disaster_tab", () -> CreativeModeTab.builder()
        .title(Component.literal("Thảm Họa Thiên Nhiên"))
        .icon(() -> new ItemStack(NUKE_CALLER.get()))
        .displayItems((p, out) -> {
            out.accept(METEOR_CALLER.get()); out.accept(EARTHQUAKE_CALLER.get()); out.accept(ACID_RAIN_CALLER.get());
            out.accept(TORNADO_CALLER.get()); out.accept(LANDSLIDE_CALLER.get()); out.accept(NUKE_CALLER.get());
            out.accept(TSUNAMI_CALLER.get()); out.accept(VOLCANO_CALLER.get()); out.accept(SINKHOLE_CALLER.get());
            out.accept(LIGHTNING_CALLER.get()); out.accept(BLIZZARD_CALLER.get()); out.accept(ZOMBIE_CALLER.get());
            out.accept(DISASTER_STOPPER.get());
        }).build()
    );

    public NaturalDisastersMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            event.getDispatcher().register(Commands.literal("disaster").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("type", StringArgumentType.word()).executes(ctx -> {
                    startDisaster(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "type").toUpperCase(), BlockPos.containing(ctx.getSource().getPosition()));
                    return 1;
                })));
        });

        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (!(event.getLevel() instanceof ServerLevel level) || level.players().isEmpty()) return;
            if (++tickCounter >= 6000) {
                tickCounter = 0;
                String[] list = {"METEOR", "EARTHQUAKE", "ACID_RAIN", "TORNADO", "LANDSLIDE", "NUKE", "TSUNAMI", "VOLCANO", "SINKHOLE", "LIGHTNING_STORM", "BLIZZARD", "ZOMBIE_APOCALYPSE"};
                ServerPlayer rp = level.players().get(RANDOM.nextInt(level.players().size()));
                startDisaster(level, list[RANDOM.nextInt(list.length)], rp.blockPosition());
            }
            if (disasterDuration > 0) {
                disasterDuration--;
                switch (activeDisaster) {
                    case "TORNADO" -> { if (tornadoPos != null) runTornado(level); }
                    case "NUKE" -> { if (targetPos != null) runNuke(level); }
                    case "TSUNAMI" -> { if (targetPos != null) runTsunami(level); }
                    case "VOLCANO" -> { if (targetPos != null) runVolcano(level); }
                    case "SINKHOLE" -> { if (targetPos != null) runSinkhole(level); }
                    default -> { for (ServerPlayer p : level.players()) runPlayerDisaster(level, p); }
                }
                if (disasterDuration == 0) { activeDisaster = "NONE"; tornadoPos = null; targetPos = null; }
            }
        });
    }
    public static class DisasterItem extends Item {
        private final String type;
        private final String displayName;
        public DisasterItem(Properties props, String type, String displayName) { super(props); this.type = type; this.displayName = displayName; }
        @Override
        public Component getName(ItemStack stack) { return Component.literal(displayName); }
        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide() && level instanceof ServerLevel sl) {
                if (type.equals("STOP")) {
                    activeDisaster = "NONE"; disasterDuration = 0; tornadoPos = null; targetPos = null;
                    player.sendSystemMessage(Component.literal("§a[HỆ THỐNG] §fĐã dừng toàn bộ thảm họa!"));
                } else startDisaster(sl, type, player.blockPosition());
                player.getCooldowns().addCooldown(this.getDefaultInstance(), 40);
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static void startDisaster(ServerLevel level, String type, BlockPos pos) {
        activeDisaster = type;
        disasterDuration = type.equals("NUKE") ? 200 : (type.equals("SINKHOLE") ? 120 : 400);
        if (type.equals("TORNADO")) tornadoPos = new Vec3(pos.getX() + 15, pos.getY(), pos.getZ() + 15);
        else if (type.equals("NUKE")) targetPos = pos.offset(RANDOM.nextInt(16) - 8, 0, RANDOM.nextInt(16) - 8);
        else if (type.equals("TSUNAMI")) { targetPos = pos.offset(-35, 0, 0); tsunamiWaveX = 0; }
        else if (type.equals("VOLCANO")) targetPos = pos.offset(24, 0, 24);
        else if (type.equals("SINKHOLE")) targetPos = pos;
        else if (type.equals("ZOMBIE_APOCALYPSE")) cmd(level, "time set midnight");
        for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.literal("§c[CẢNH BÁO] §eThiên tai: §l" + type));
    }

    private static void cmd(ServerLevel l, String c) {
        l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withSuppressedOutput(), c);
    }

    private static void runTsunami(ServerLevel level) {
        if (disasterDuration % 4 == 0 && tsunamiWaveX < 70) {
            int cx = targetPos.getX() + tsunamiWaveX;
            for (int z = -25; z <= 25; z++) {
                for (int y = 0; y < 14; y++) {
                    BlockPos wp = new BlockPos(cx, targetPos.getY() + y, targetPos.getZ() + z);
                    if (level.getBlockState(wp).canBeReplaced()) level.setBlockAndUpdate(wp, Blocks.WATER.defaultBlockState());
                }
            }
            tsunamiWaveX++;
        }
    }

    private static void runVolcano(ServerLevel level) {
        if (disasterDuration > 240) {
            int h = (400 - disasterDuration) / 5;
            int r = Math.max(4, 26 - (int)(h * 0.7));
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        BlockPos bp = targetPos.offset(x, h, z);
                        boolean isCore = (x * x + z * z <= 12 && h > 8);
                        BlockState state = isCore ? Blocks.LAVA.defaultBlockState() : (RANDOM.nextBoolean() ? Blocks.BASALT.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
                        level.setBlockAndUpdate(bp, state);
                    }
                }
            }
            if (disasterDuration == 241) {
                level.explode(null, targetPos.getX(), targetPos.getY() + 32, targetPos.getZ(), 10.0f, true, Level.ExplosionInteraction.BLOCK);
                cmd(level, "weather thunder 30");
            }
        } else {
            BlockPos top = targetPos.above(30);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.getX(), top.getY() + 3, top.getZ(), 40, 3.0, 4.0, 3.0, 0.08);
            level.sendParticles(ParticleTypes.LAVA, top.getX(), top.getY() + 2, top.getZ(), 30, 3.0, 2.0, 3.0, 0.2);

            if (disasterDuration % 4 == 0) {
                for (int i = 0; i < 4; i++) {
                    double vx = (RANDOM.nextDouble() - 0.5) * 3.2;
                    double vz = (RANDOM.nextDouble() - 0.5) * 3.2;
                    LargeFireball fb = new LargeFireball(level, level.players().get(0), new Vec3(vx, 1.1, vz), 4);
                    fb.setPos(top.getX() + RANDOM.nextInt(5) - 2, top.getY() + 3, top.getZ() + RANDOM.nextInt(5) - 2);
                    level.addFreshEntity(fb);
                }
                BlockPos spill = top.offset(RANDOM.nextInt(7) - 3, 1, RANDOM.nextInt(7) - 3);
                level.setBlockAndUpdate(spill, Blocks.LAVA.defaultBlockState());
            }

            for (int i = 0; i < 6; i++) {
                BlockPos ground = targetPos.offset(RANDOM.nextInt(50) - 25, RANDOM.nextInt(6) - 2, RANDOM.nextInt(50) - 25);
                if (level.getBlockState(ground).isSolid() && level.getBlockState(ground.above()).isAir()) {
                    level.setBlockAndUpdate(ground, Blocks.MAGMA_BLOCK.defaultBlockState());
                    if (RANDOM.nextInt(3) == 0) level.setBlockAndUpdate(ground.above(), Blocks.FIRE.defaultBlockState());
                }
            }
        }
    }

    private static void runSinkhole(ServerLevel level) {
        if (disasterDuration % 3 == 0) {
            int depth = (120 - disasterDuration) / 3;
            int r = 9;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        BlockPos bp = targetPos.offset(x, -depth, z);
                        if (!level.getBlockState(bp).is(Blocks.BEDROCK)) level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }
    private static void runTornado(ServerLevel level) {
        Vec3 dir = level.players().get(0).position().subtract(tornadoPos).normalize();
        tornadoPos = tornadoPos.add(dir.x * 0.25, 0, dir.z * 0.25);
        for (int y = 0; y < 30; y++) {
            double r = 1.5 + y * 0.25;
            for (int i = 0; i < 6; i++) {
                double a = disasterDuration * 0.4 + i * Math.PI / 3 + y * 0.2;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, tornadoPos.x + Math.cos(a) * r, tornadoPos.y + y, tornadoPos.z + Math.sin(a) * r, 1, 0, 0, 0, 0.01);
            }
        }
        for (Entity e : level.getEntities(null, new AABB(tornadoPos.x - 18, tornadoPos.y - 5, tornadoPos.z - 18, tornadoPos.x + 18, tornadoPos.y + 35, tornadoPos.z + 18))) {
            double dx = tornadoPos.x - e.getX(), dz = tornadoPos.z - e.getZ();
            e.setDeltaMovement(new Vec3(dx * 0.08 - dz * 0.12, 0.35, dz * 0.08 + dx * 0.12)); e.hurtMarked = true;
        }
    }

    private static void runNuke(ServerLevel level) {
        if (disasterDuration > 100) {
            if (disasterDuration % 20 == 0) {
                for (ServerPlayer p : level.players()) {
                    p.sendSystemMessage(Component.literal("§4§l[HẠT NHÂN] §eNổ sau: §c" + ((disasterDuration - 100) / 20) + "s"));
                    level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1f, 0.5f);
                }
            }
            level.sendParticles(ParticleTypes.FLAME, targetPos.getX(), targetPos.getY() + 2, targetPos.getZ(), 15, 0.5, 10, 0.5, 0.05);
        } else if (disasterDuration == 100) {
            level.explode(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), 22f, true, Level.ExplosionInteraction.BLOCK);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(targetPos).inflate(45))) {
                le.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 1));
                le.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
        } else level.sendParticles(ParticleTypes.ASH, targetPos.getX(), targetPos.getY() + 15, targetPos.getZ(), 80, 20, 10, 20, 0.02);
    }

    private static void runPlayerDisaster(ServerLevel level, ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        switch (activeDisaster) {
            case "METEOR" -> {
                if (disasterDuration % 20 == 0) {
                    LargeFireball fb = new LargeFireball(level, p, new Vec3(0, -1.5, 0), 3);
                    fb.setPos(pos.getX() + RANDOM.nextInt(30) - 15, pos.getY() + 35, pos.getZ() + RANDOM.nextInt(30) - 15);
                    level.addFreshEntity(fb);
                }
            }
            case "EARTHQUAKE" -> {
                p.setDeltaMovement(p.getDeltaMovement().add((RANDOM.nextDouble() - 0.5) * 0.4, 0.15, (RANDOM.nextDouble() - 0.5) * 0.4)); p.hurtMarked = true;
                if (disasterDuration % 5 == 0) {
                    BlockPos cp = pos.offset(RANDOM.nextInt(12) - 6, -1, RANDOM.nextInt(12) - 6);
                    if (!level.getBlockState(cp).isAir() && level.getBlockState(cp).getDestroySpeed(level, cp) >= 0) level.destroyBlock(cp, false);
                }
            }
            case "ACID_RAIN" -> {
                cmd(level, "weather thunder 20");
                if (level.canSeeSky(pos)) {
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 1));
                    if (level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
                }
            }
            case "LANDSLIDE" -> {
                if (disasterDuration % 3 == 0) {
                    for (int i = 0; i < 4; i++) {
                        BlockPos sp = pos.offset(RANDOM.nextInt(20) - 10, RANDOM.nextInt(8) + 2, RANDOM.nextInt(20) - 10);
                        BlockState st = level.getBlockState(sp);
                        if (!st.isAir() && (st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.STONE) || st.is(Blocks.SAND))) {
                            level.removeBlock(sp, false); FallingBlockEntity.fall(level, sp, st);
                        } else if (st.isAir()) FallingBlockEntity.fall(level, sp.above(5), RANDOM.nextBoolean() ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    }
                }
            }
            case "LIGHTNING_STORM" -> {
                cmd(level, "weather thunder 20");
                if (disasterDuration % 10 == 0) {
                    BlockPos lp = pos.offset(RANDOM.nextInt(32) - 16, 0, RANDOM.nextInt(32) - 16);
                    cmd(level, "summon minecraft:lightning_bolt " + lp.getX() + " " + lp.getY() + " " + lp.getZ());
                }
            }
            case "BLIZZARD" -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX(), pos.getY() + 6, pos.getZ(), 60, 12, 5, 12, 0.05);
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                if (disasterDuration % 10 == 0 && level.canSeeSky(pos)) p.hurt(level.damageSources().freeze(), 1.0f);
                for (int i = 0; i < 5; i++) {
                    BlockPos fp = pos.offset(RANDOM.nextInt(20) - 10, -1, RANDOM.nextInt(20) - 10);
                    if (level.getBlockState(fp).is(Blocks.WATER)) level.setBlockAndUpdate(fp, Blocks.ICE.defaultBlockState());
                    else if (level.getBlockState(fp.above()).isAir() && level.getBlockState(fp).isSolid()) level.setBlockAndUpdate(fp.above(), Blocks.SNOW.defaultBlockState());
                }
            }
            case "ZOMBIE_APOCALYPSE" -> {
                if (disasterDuration % 40 == 0) {
                    BlockPos zp = pos.offset(RANDOM.nextInt(24) - 12, 1, RANDOM.nextInt(24) - 12);
                    cmd(level, "summon minecraft:zombie " + zp.getX() + " " + zp.getY() + " " + zp.getZ());
                }
            }
        }
    }
}
