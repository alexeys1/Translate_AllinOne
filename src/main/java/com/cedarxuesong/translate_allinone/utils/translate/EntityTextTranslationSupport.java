package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
public final class EntityTextTranslationSupport {
    private static final int MAX_NAME_TAG_CHARACTERS = 256;

    private EntityTextTranslationSupport() {
    }

    public static Component translateNameTag(Entity entity, Component originalNameTag) {
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()
                || !isFeatureEnabled(config)
                || !config.translate_entity_name_tags
                || !isEligibleEntity(entity, config)
                || !isWithinRadius(entity, config)
                || !ComponentRenderTranslationSupport.isEligible(originalNameTag, MAX_NAME_TAG_CHARACTERS)) {
            return originalNameTag;
        }
        String type = entity.getType() == null ? "unknown" : entity.getType().getDescriptionId();
        String context = "entity:name_tag; type=" + type;
        if (!ComponentRenderTranslationSupport.shouldRenderTranslated(config)) {
            ComponentRenderTranslationSupport.forceRefreshAndQueue(
                    originalNameTag,
                    ComponentTranslationRoute.ENTITY_NAME,
                    context,
                    "entity-name-v1",
                    config
            );
            return originalNameTag;
        }
        ComponentRenderTranslationSupport.TranslationResult result = ComponentRenderTranslationSupport.translate(
                originalNameTag,
                ComponentTranslationRoute.ENTITY_NAME,
                context,
                "entity-name-v1",
                config,
                ComponentRenderTranslationSupport.isRefreshPressed(config)
        );
        return ComponentRenderTranslationSupport.displayWithPendingAnimation(
                result,
                "entity:name_tag:" + entity.getId()
        );
    }

    static boolean isFeatureEnabled(OtherTranslationsConfig config) {
        return ComponentRenderTranslationSupport.isFeatureEnabled(
                config,
                config != null && config.enabled_translate_entity_text
        );
    }

    static boolean isWithinRadius(Entity entity, OtherTranslationsConfig config) {
        if (entity == null || config == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        Player player = client == null ? null : client.player;
        if (player == null) {
            return false;
        }
        int radius = Math.clamp(config.entity_translation_radius, 1, 16);
        return player.position().distanceToSqr(entity.position()) <= (double) radius * radius;
    }

    private static boolean isEligibleEntity(Entity entity, OtherTranslationsConfig config) {
        if (entity == null || entity instanceof Player || !entity.hasCustomName()) {
            return false;
        }
        if (entity instanceof ItemEntity) {
            return config.translate_item_entity_hover_labels;
        }
        return true;
    }

}
