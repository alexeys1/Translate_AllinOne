package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.versionapi.ComponentCodec;
import com.cedarxuesong.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public record ComponentTranslationBundle(
        ComponentTranslationDocument cacheDocument,
        List<ComponentTranslationDocument> documents,
        List<ComponentDynamicTemplate> templates,
        List<SemanticSlot> semanticSlots,
        List<AccentAnchor> accentAnchors
) {
    private static final ComponentCodec<Component> COMPONENT_CODEC = MinecraftComponentCodec.INSTANCE;

    public ComponentTranslationBundle {
        if (cacheDocument == null || documents == null || templates == null || semanticSlots == null || accentAnchors == null) {
            throw new IllegalArgumentException("Component translation bundle is incomplete.");
        }
        documents = List.copyOf(documents);
        templates = List.copyOf(templates);
        semanticSlots = List.copyOf(semanticSlots);
        accentAnchors = List.copyOf(accentAnchors);
        if (documents.size() != templates.size()) {
            throw new IllegalArgumentException("Component translation bundle templates do not match its documents.");
        }
        if (documents.isEmpty() && cacheDocument.route() != ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            throw new IllegalArgumentException("Component translation bundle has no local documents.");
        }
    }

    public record SemanticSlot(
            String id,
            int styleId,
            String sourceText
    ) {
        public SemanticSlot {
            new ComponentTranslationBundleCore.SemanticSlot(id, styleId, sourceText);
        }

        public String placeholder() {
            return "{" + id + "}";
        }
    }

    public record AccentAnchor(String id) {
        public AccentAnchor {
            new ComponentTranslationBundleCore.AccentAnchor(id);
        }

        public String beginToken() {
            return "{" + id + ".begin}";
        }

        public String endToken() {
            return "{" + id + ".end}";
        }
    }

    public static ComponentTranslationBundle create(
            List<Component> components,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        return create(components, route, context, policyVersion, false);
    }

    public static ComponentTranslationBundle create(
            List<Component> components,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            boolean isolateBatchContext
    ) {
        if (components == null || components.isEmpty() || route == null) {
            throw new IllegalArgumentException("Component translation bundle input is incomplete.");
        }

        List<ComponentDynamicTemplate> templates = new ArrayList<>(components.size());
        for (Component component : components) {
            templates.add(ComponentDynamicTemplate.prepare(component));
        }
        ComponentTranslationBundleCore core = ComponentTranslationBundleCore.createOrdered(
                jsonTemplates(templates),
                templateTexts(templates),
                route,
                context,
                policyVersion,
                isolateBatchContext
        );
        return fromCore(core, templates);
    }

    public static ComponentTranslationBundle createCoherentParagraph(
            List<Component> components,
            String context,
            String policyVersion,
            String paragraphTemplate
    ) {
        return createCoherentParagraph(components, context, policyVersion, paragraphTemplate, List.of());
    }

    public static ComponentTranslationBundle createCoherentParagraph(
            List<Component> components,
            String context,
            String policyVersion,
            String paragraphTemplate,
            List<SemanticSlot> semanticSlots
    ) {
        if (components == null
                || components.isEmpty()
                || paragraphTemplate == null
                || semanticSlots == null
                || paragraphTemplate.isBlank()) {
            throw new IllegalArgumentException("Coherent paragraph translation input is incomplete.");
        }

        List<ComponentDynamicTemplate> templates = new ArrayList<>(components.size());
        for (Component component : components) {
            if (component == null) {
                throw new IllegalArgumentException("Coherent paragraph contains a null Component.");
            }
            templates.add(ComponentDynamicTemplate.prepare(component));
        }
        ComponentTranslationBundleCore core = ComponentTranslationBundleCore.createCoherentParagraph(
                jsonTemplates(templates),
                context,
                policyVersion,
                paragraphTemplate,
                coreSemanticSlots(semanticSlots)
        );
        return fromCore(core, templates);
    }

    public static ComponentTranslationBundle createInlineAnchoredParagraph(
            String context,
            String policyVersion,
            String paragraphTemplate,
            List<String> hardTokenIds,
            List<String> accentIds
    ) {
        ComponentTranslationBundleCore core = ComponentTranslationBundleCore.createInlineAnchoredParagraph(
                context,
                policyVersion,
                paragraphTemplate,
                hardTokenIds,
                accentIds
        );
        return fromCore(core, List.of());
    }

    public List<Component> apply(ComponentTranslationResponse response) {
        return core().applyToJson(response).stream()
                .map(COMPONENT_CODEC::decode)
                .toList();
    }

    public String coherentParagraphTranslation(ComponentTranslationResponse response) {
        return core().coherentParagraphTranslation(response);
    }

    public String coherentSafeBodyParagraphTranslation(
            ComponentTranslationResponse response,
            Integer bodyStyleId
    ) {
        return core().coherentSafeBodyParagraphTranslation(response, bodyStyleId);
    }

    public ComponentTranslationResponse promoteLegacyCoherentParagraphResponse(
            ComponentTranslationBundle legacyBundle,
            ComponentTranslationResponse legacyResponse
    ) {
        return core().promoteLegacyCoherentParagraphResponse(
                legacyBundle == null ? null : legacyBundle.core(),
                legacyResponse
        );
    }

    private ComponentTranslationBundleCore core() {
        return new ComponentTranslationBundleCore(
                cacheDocument,
                documents,
                jsonTemplates(templates),
                coreSemanticSlots(semanticSlots),
                coreAccentAnchors(accentAnchors)
        );
    }

    private static ComponentTranslationBundle fromCore(
            ComponentTranslationBundleCore core,
            List<ComponentDynamicTemplate> templates
    ) {
        List<SemanticSlot> semanticSlots = core.semanticSlots().stream()
                .map(slot -> new SemanticSlot(slot.id(), slot.styleId(), slot.sourceText()))
                .toList();
        List<AccentAnchor> accentAnchors = core.accentAnchors().stream()
                .map(anchor -> new AccentAnchor(anchor.id()))
                .toList();
        return new ComponentTranslationBundle(
                core.cacheDocument(),
                core.documents(),
                templates,
                semanticSlots,
                accentAnchors
        );
    }

    private static List<ComponentDynamicJsonTemplate> jsonTemplates(
            List<ComponentDynamicTemplate> templates
    ) {
        return templates.stream().map(ComponentDynamicTemplate::jsonTemplate).toList();
    }

    private static List<String> templateTexts(List<ComponentDynamicTemplate> templates) {
        return templates.stream()
                .map(ComponentDynamicTemplate::templateComponent)
                .map(Component::getString)
                .toList();
    }

    private static List<ComponentTranslationBundleCore.SemanticSlot> coreSemanticSlots(
            List<SemanticSlot> semanticSlots
    ) {
        return semanticSlots.stream()
                .map(slot -> slot == null
                        ? null
                        : new ComponentTranslationBundleCore.SemanticSlot(
                                slot.id(),
                                slot.styleId(),
                                slot.sourceText()
                        ))
                .toList();
    }

    private static List<ComponentTranslationBundleCore.AccentAnchor> coreAccentAnchors(
            List<AccentAnchor> accentAnchors
    ) {
        return accentAnchors.stream()
                .map(anchor -> anchor == null
                        ? null
                        : new ComponentTranslationBundleCore.AccentAnchor(anchor.id()))
                .toList();
    }
}
