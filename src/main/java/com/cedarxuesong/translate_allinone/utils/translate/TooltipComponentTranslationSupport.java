package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import net.minecraft.text.Text;

/** Component-cache path for ordinary tooltip template lines. */
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
        PreparedLineDocument preparedDocument = prepareLineDocument(prepared, route, context, policyVersion, config);
        if (preparedDocument == null) {
            return null;
        }

        ComponentTranslationDocument document = preparedDocument.document();
        String targetLanguage = config.target_language;
        String cacheKey = ComponentTranslationRuntime.cacheKey(document, targetLanguage);
        if (TooltipRefreshNoticeSupport.consumeComponentRefresh(cacheKey, config)) {
            ComponentTranslationRuntime.forceRefresh(document, targetLanguage);
        }
        ComponentTranslationApplier applier = new ComponentTranslationApplier();
        ComponentTranslationRuntime.Resolution<Text> resolution = ComponentTranslationRuntime.resolve(
                document,
                targetLanguage,
                TooltipTemplateRuntime.buildLegacyCompatibilityKey(prepared.sourceLine()),
                () -> TooltipTemplateRuntime.peekTranslatedPreparedTemplate(prepared),
                response -> {
                    long startedAt = System.nanoTime();
                    try {
                        Text translatedTemplate = applier.apply(document, response);
                        Text translated = TooltipTemplateRuntime.renderComponentTemplateTranslation(
                                preparedDocument.renderTemplate(),
                                translatedTemplate.getString()
                        );
                        if (translated == null) {
                            throw new IllegalArgumentException("Component tooltip template could not be rendered.");
                        }
                        return translated;
                    } finally {
                        ComponentTranslationMetrics.recordNanos(
                                route,
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                    }
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
        PreparedLineDocument preparedDocument = prepareLineDocument(prepared, route, context, policyVersion, config);
        if (preparedDocument == null) {
            return 0;
        }
        ComponentTranslationDocument document = preparedDocument.document();
        String cacheKey = ComponentTranslationRuntime.cacheKey(document, config.target_language);
        TooltipRefreshNoticeSupport.markComponentRefreshHandled(cacheKey);
        return ComponentTranslationRuntime.forceRefresh(document, config.target_language) ? 1 : 0;
    }

    private static PreparedLineDocument prepareLineDocument(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        if (prepared == null || config == null || !isEligibleLine(prepared.sourceLine(), config)) {
            return null;
        }
        try {
            PreparedTooltipTemplate renderTemplate = TooltipTemplateRuntime.prepareComponentTemplate(prepared);
            if (renderTemplate == null || renderTemplate.normalizedTemplate() == null) {
                return null;
            }
            ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                    Text.literal(renderTemplate.normalizedTemplate()),
                    route,
                    context,
                    policyVersion
            );
            return document.units().isEmpty() ? null : new PreparedLineDocument(document, renderTemplate);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static TooltipTranslationSupport.TooltipLineResult toLineResult(
            Text original,
            String cacheKey,
            ComponentTranslationRuntime.Resolution<Text> resolution
    ) {
        if ((resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
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

    static boolean isEligibleLine(Text line, ItemTranslateConfig config) {
        if (line == null || line.getString().isBlank() || line.getString().indexOf('\uFFFD') >= 0) {
            return false;
        }
        return !TooltipTemplateRuntime.hasUnsafeMixedDecorativeLiteral(line);
    }

    private record PreparedLineDocument(
            ComponentTranslationDocument document,
            PreparedTooltipTemplate renderTemplate
    ) {
    }
}
