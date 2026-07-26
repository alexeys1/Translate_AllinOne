package com.cedarxuesong.translate_allinone.utils.translate;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import net.minecraft.network.chat.Component;
final class TooltipComponentTranslationSupport {
    private TooltipComponentTranslationSupport() {
    }
    static TooltipTranslationSupport.TooltipLineResult translatePreparedLine(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        if (prepared == null || config == null || !isEligibleLine(prepared)) {
            return null;
        }
        PreparedLineDocument preparedDocument = prepareLineDocument(prepared, route, context, policyVersion);
        if (preparedDocument == null) {
            return null;
        }
        ComponentTranslationDocument document = preparedDocument.document();
        String targetLanguage = config.target_language;
        String cacheKey = ComponentTranslationRuntime.cacheKey(document, targetLanguage);
        if (TooltipRefreshNoticeSupport.consumeV1Refresh(cacheKey, config)) {
            ComponentTranslationRuntime.forceRefresh(document, targetLanguage);
        }
        ComponentTranslationApplier applier = new ComponentTranslationApplier();
        ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                document,
                targetLanguage,
                TooltipTemplateRuntime.buildLegacyCompatibilityKey(prepared.sourceLine()),
                () -> TooltipTemplateRuntime.peekTranslatedPreparedTemplate(prepared),
                response -> {
                    long startedAt = System.nanoTime();
                    Component translatedTemplate = applier.apply(document, response);
                    Component translated = TooltipTemplateRuntime.renderComponentV1TemplateTranslation(
                            preparedDocument.renderTemplate(),
                            translatedTemplate.getString()
                    );
                    if (translated == null) {
                        throw new IllegalArgumentException("Component V1 tooltip template could not be rendered.");
                    }
                    ComponentTranslationMetrics.recordNanos(
                            route,
                            ComponentTranslationMetrics.Timing.APPLY,
                            System.nanoTime() - startedAt
                    );
                    return translated;
                },
                context
        );
        return toLineResult(prepared.sourceLine(), cacheKey, resolution);
    }
    static int forceRefreshPreparedLine(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        if (config == null) {
            return 0;
        }
        PreparedLineDocument preparedDocument = prepareLineDocument(prepared, route, context, policyVersion);
        if (preparedDocument == null) {
            return 0;
        }
        ComponentTranslationDocument document = preparedDocument.document();
        String cacheKey = ComponentTranslationRuntime.cacheKey(document, config.target_language);
        TooltipRefreshNoticeSupport.markV1RefreshHandled(cacheKey);
        return ComponentTranslationRuntime.forceRefresh(document, config.target_language) ? 1 : 0;
    }
    private static PreparedLineDocument prepareLineDocument(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        if (!isEligibleLine(prepared)) {
            return null;
        }
        try {
            PreparedTooltipTemplate renderTemplate = TooltipTemplateRuntime.prepareComponentV1Template(prepared);
            ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                    Component.literal(renderTemplate.normalizedTemplate()),
                    route,
                    context,
                    policyVersion
            );
            return document.units().isEmpty() ? null : new PreparedLineDocument(document, renderTemplate);
        } catch (RuntimeException e) {
            return null;
        }
    }
    private static TooltipTranslationSupport.TooltipLineResult toLineResult(
            Component original,
            String cacheKey,
            ComponentTranslationRuntime.Resolution<Component> resolution
    ) {
        if ((resolution.state() == ComponentTranslationRuntime.State.V1_HIT
                || resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT)
                && resolution.value() != null) {
            return new TooltipTranslationSupport.TooltipLineResult(resolution.value(), false, false);
        }
        if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
            return new TooltipTranslationSupport.TooltipLineResult(
                    AnimationManager.getAnimatedStyledText(original, cacheKey, false),
                    true,
                    false
            );
        }
        if (resolution.state() == ComponentTranslationRuntime.State.FAILED) {
            return new TooltipTranslationSupport.TooltipLineResult(
                    original,
                    false,
                    false,
                    resolution.errorMessage()
            );
        }
        return new TooltipTranslationSupport.TooltipLineResult(original, false, false);
    }
    private static boolean isEligibleLine(PreparedTooltipTemplate prepared) {
        if (prepared == null || prepared.sourceLine() == null || prepared.sourceLine().getString().isBlank()) {
            return false;
        }
        if (prepared.sourceLine().getString().indexOf('§') >= 0) {
            return false;
        }
        return !prepared.useTagStylePreservation();
    }
    private record PreparedLineDocument(
            ComponentTranslationDocument document,
            PreparedTooltipTemplate renderTemplate
    ) {
    }
}
