package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentDocumentBuilder;
import com.alexeys.translate_allinone.utils.componentjson.ComponentDynamicTemplate;
import com.alexeys.translate_allinone.utils.componentjson.ComponentJsonCodec;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTextUnit;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationPolicy;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationResponse;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

public final class ScoreboardEntryTemplate {
    public static final String POLICY_VERSION = "scoreboard";
    private static final String OWNER_POINTER_PREFIX = "/extra/1";
    private static final ComponentTranslationApplier APPLIER = new ComponentTranslationApplier();

    public record Prepared(
            Component original,
            ComponentTranslationDocument document,
            ComponentDynamicTemplate template
    ) {
        public Prepared {
            original = original == null ? Component.empty() : original.copy();
        }

        public Component renderOriginal() {
            return original.copy();
        }

        public Component renderTranslated(ComponentTranslationResponse response) {
            if (document == null) {
                throw new IllegalStateException("Scoreboard Component document is unavailable.");
            }
            Component translated = APPLIER.apply(document, response);
            return template == null ? translated : template.restore(translated);
        }
    }

    private ScoreboardEntryTemplate() {
    }

    public static Prepared prepare(
            Component prefix,
            Component owner,
            boolean translateOwner,
            boolean protectOwner,
            Component suffix
    ) {
        Component original = prepareComponentSource(copyOrEmpty(prefix), copyOrEmpty(owner), copyOrEmpty(suffix));
        boolean ownerIsPassthrough = protectOwner || !translateOwner;
        String ownerText = owner == null ? "" : owner.getString();
        ComponentDynamicTemplate template = ComponentDynamicTemplate.prepare(
                original,
                ownerIsPassthrough && !ownerText.isBlank() ? Set.of(ownerText) : Set.of()
        );
        return new Prepared(
                original,
                prepareDocument(template.templateComponent(), translateOwner, protectOwner),
                template
        );
    }

    private static ComponentTranslationDocument prepareDocument(
            Component sourceComponent,
            boolean translateOwner,
            boolean protectOwner
    ) {
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.SCOREBOARD)
                .withContext("scoreboard_entry")
                .withSemanticSetting("route_policy", POLICY_VERSION)
                .withSemanticSetting("layout", "prefix-owner-suffix")
                .withSemanticSetting("translate_prefix_suffix", "true")
                .withSemanticSetting("translate_owner", Boolean.toString(translateOwner))
                .withSemanticSetting("owner_protected", Boolean.toString(protectOwner));
        ComponentTranslationDocument extracted = new ComponentDocumentBuilder().build(sourceComponent, policy);
        if (translateOwner && !protectOwner) {
            return extracted;
        }

        List<ComponentTextUnit> filteredUnits = extracted.units().stream()
                .filter(unit -> !isOwnerPointer(unit.jsonPointer()))
                .toList();
        return new ComponentTranslationDocument(
                extracted.protocol(),
                extracted.policyVersion(),
                extracted.route(),
                extracted.sourceJson(),
                filteredUnits,
                extracted.semanticSettings()
        );
    }

    private static Component prepareComponentSource(Component prefix, Component owner, Component suffix) {
        JsonObject source = new JsonObject();
        source.addProperty("text", "");
        JsonArray segments = new JsonArray();
        segments.add(ComponentJsonCodec.encode(prefix));
        segments.add(ComponentJsonCodec.encode(owner));
        segments.add(ComponentJsonCodec.encode(suffix));
        source.add("extra", segments);
        return ComponentJsonCodec.decode(source);
    }

    private static boolean isOwnerPointer(String pointer) {
        return OWNER_POINTER_PREFIX.equals(pointer)
                || (pointer != null && pointer.startsWith(OWNER_POINTER_PREFIX + "/"));
    }

    private static Component copyOrEmpty(Component component) {
        return component == null ? Component.empty() : component.copy();
    }
}
