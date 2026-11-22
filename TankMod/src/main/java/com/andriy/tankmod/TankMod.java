package com.andriy.tankmod;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    public static final RegistryObject<EntityType<TankShellEntity>> TANK_SHELL = ENTITIES.register("tank_shell",
            () -> EntityType.Builder.<TankShellEntity>of(TankShellEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("tank_shell"));

    public TankMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(this::addToCreativeTab);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(TANK_CANNON.get());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TANK_SHELL.get(), NoopRenderer::new);
        }
    }

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
            if (ageTicks <= 3) {
                return;
            }
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
