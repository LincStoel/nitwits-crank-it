package com.lincstoel.nitwitscrankit.compat;

import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.ai.NitwitStatus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade integration: looking at a nitwit shows what it's up to ("Cranking", "Dizzy", "Pranking: steam whistle"...).
 * Only loaded by Jade itself, so nothing here runs without it. Needs the mod on both sides, since nitwit brains
 * only exist on the server.
 */
@WailaPlugin
public class JadeCompat implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(StatusProvider.INSTANCE, Villager.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(StatusProvider.INSTANCE, Villager.class);
    }

    private enum StatusProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(NitwitsCrankIt.MOD_ID, "nitwit_status");
        private static final String KEY = "NitwitStatus";

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (accessor.getEntity() instanceof Villager villager) {
                String status = NitwitStatus.translationKey(villager);
                if (status != null) {
                    data.putString(KEY, status);
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains(KEY)) {
                tooltip.add(Component.translatable(data.getString(KEY)));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
