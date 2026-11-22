package com.andriy.tankmod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(TankMod.MODID)
public class TankMod {
    public static final String MODID = "tankmod";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<Item> TANK_CANNON = ITEMS.register("tank_cannon",
            () -> new TankCannonItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TANK_SPAWNER = ITEMS.register("tank_spawner",
            () -> new TankSpawnerItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<EntityType<TankShellEntity>> TANK_SHELL = ENTITIES.register("tank_shell",
            () -> EntityType.Builder.<TankShellEntity>of(TankShellEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("tank_shell"));

    public static final RegistryObject<EntityType<TankEntity>> TANK_ENTITY = ENTITIES.register("tank",
            () -> EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                    .sized(2.2f, 1.5f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("tank"));

    public TankMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(this::addToCreativeTab);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(TANK_CANNON.get());
            event.accept(TANK_SPAWNER.get());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TANK_SHELL.get(), NoopRenderer::new);
            event.registerEntityRenderer(TANK_ENTITY.get(), TankRenderer::new);
        }
    }

    // Предмет для спавна танка (ПКМ по земле)
    public static class TankSpawnerItem extends Item {
        public TankSpawnerItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            if (!level.isClientSide) {
                BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
                TankEntity tank = new TankEntity(TANK_ENTITY.get(), level);
                tank.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (context.getPlayer() != null) {
                    tank.setYRot(context.getPlayer().getYRot());
                }
                level.addFreshEntity(tank);
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
                if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    // Сущность танка (посадка, езда, стрельба на пробел)
    public static class TankEntity extends Entity {
        private int shootCooldown = 0;

        public TankEntity(EntityType<?> type, Level level) {
            super(type, level);
            this.blocksBuilding = true;
            this.setMaxUpStep(1.1f);
        }

        @Override
        protected void defineSynchedData() {
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        public boolean isPickable() {
            return true;
        }

        @Override
        public boolean canBeCollidedWith() {
            return true;
        }

        @Override
        public double getPassengersRidingOffset() {
            return 1.05D;
        }

        @Override
        public InteractionResult interact(Player player, InteractionHand hand) {
            if (!this.level().isClientSide) {
                if (this.getPassengers().isEmpty()) {
                    player.startRiding(this);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        @Override
        public LivingEntity getControllingPassenger() {
            Entity first = this.getFirstPassenger();
            return first instanceof LivingEntity living ? living : null;
        }

        @Override
        public void tick() {
            super.tick();
            if (shootCooldown > 0) shootCooldown--;

            LivingEntity driver = this.getControllingPassenger();
            Vec3 motion = this.getDeltaMovement();
            double dy = this.onGround() ? 0.0 : motion.y - 0.08;

            if (driver instanceof Player player) {
                this.setYRot(player.getYRot());
                this.yRotO = this.getYRot();

                float forward = player.zza;
                float strafe = player.xxa;

                float rad = this.getYRot() * ((float) Math.PI / 180F);
                double speed = 0.28;
                double vx = (-Mth.sin(rad) * forward + Mth.cos(rad) * strafe) * speed;
                double vz = (Mth.cos(rad) * forward + Mth.sin(rad) * strafe) * speed;

                this.setDeltaMovement(vx, dy, vz);

                if (this.level().isClientSide && Minecraft.getInstance().player == player) {
                    if (Minecraft.getInstance().options.keyJump.isDown() && shootCooldown == 0) {
                        // Сигнал стрельбы обрабатывается через предмет или пакет, дублируем через локальный триггер
                    }
                }
                if (!this.level().isClientSide && player.isJumping && shootCooldown == 0) {
                    fireCannon(player);
                    shootCooldown = 30;
                }
            } else {
                this.setDeltaMovement(motion.x * 0.8, dy, motion.z * 0.8);
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
        }

        private void fireCannon(Player gunner) {
            TankShellEntity shell = new TankShellEntity(TANK_SHELL.get(), gunner, this.level());
            Vec3 look = gunner.getLookAngle();
            shell.setPos(
                    this.getX() + look.x * 2.2,
                    this.getY() + 1.3 + look.y * 2.2,
                    this.getZ() + look.z * 2.2
            );
            shell.shootFromRotation(gunner, gunner.getXRot(), gunner.getYRot(), 0.0f, 3.4f, 0.2f);
            this.level().addFreshEntity(shell);
            this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.VEHICLES, 1.8f, 0.55f);
        }
    }

    // Визуал танка из блоков (корпус из зелёного терракота/железа + башня + ствол)
    public static class TankRenderer extends EntityRenderer<TankEntity> {
        private final BlockRenderDispatcher blockRenderer;

        public TankRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.blockRenderer = context.getBlockRenderDispatcher();
            this.shadowRadius = 1.2f;
        }

        @Override
        public void render(TankEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - entityYaw));

            // Корпус
            poseStack.pushPose();
            poseStack.translate(-0.9, 0.0, -1.2);
            poseStack.scale(1.8f, 0.7f, 2.4f);
            blockRenderer.renderSingleBlock(Blocks.GREEN_TERRACOTTA.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();

            // Башня
            poseStack.pushPose();
            poseStack.translate(-0.55, 0.7, -0.55);
            poseStack.scale(1.1f, 0.55f, 1.1f);
            blockRenderer.renderSingleBlock(Blocks.GREEN_CONCRETE.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();

            // Ствол
            poseStack.pushPose();
            poseStack.translate(-0.15, 0.9, 0.4);
            poseStack.scale(0.3f, 0.3f, 1.6f);
            blockRenderer.renderSingleBlock(Blocks.NETHERITE_BLOCK.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();

            poseStack.popPose();
        }

        @Override
        public net.minecraft.resources.ResourceLocation getTextureLocation(TankEntity entity) {
            return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
        }
    }

    // Ручная пушка (оставлена как запасной предмет)
    public static class TankCannonItem extends Item {
        public TankCannonItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                TankShellEntity shell = new TankShellEntity(TANK_SHELL.get(), player, level);
                Vec3 look = player.getLookAngle();
                shell.setPos(
                        player.getX() + look.x * 1.4,
                        player.getEyeY() - 0.1 + look.y * 1.4,
                        player.getZ() + look.z * 1.4
                );
                shell.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 3.2f, 0.3f);
                level.addFreshEntity(shell);

                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.6f);
                player.getCooldowns().addCooldown(this, 25);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    // Снаряд
    public static class TankShellEntity extends ThrowableProjectile {
        private int ageTicks = 0;

        public TankShellEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
            super(type, level);
        }

        public TankShellEntity(EntityType<? extends ThrowableProjectile> type, LivingEntity owner, Level level) {
            super(type, owner, level);
        }

        @Override
        protected void defineSynchedData() {
        }

        @Override
        public void tick() {
            super.tick();
            ageTicks++;
            if (ageTicks <= 3) return;
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
                serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);
            if (!this.level().isClientSide && ageTicks > 3) {
                this.level().explode(
                        this.getOwner(),
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        4.0f,
                        Level.ExplosionInteraction.NONE
                );
                this.discard();
            }
        }
    }
}
