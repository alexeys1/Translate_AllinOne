package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import net.minecraft.network.chat.Component;
public record BookPageTranslationSnapshot(
        int pageIndex,
        Component originalPage,
        Component displayedPage,
        ComponentTranslationRuntime.State state
) {
    public boolean isTranslated() {
        return state == ComponentTranslationRuntime.State.CACHE_HIT
                && displayedPage != null
                && !displayedPage.equals(originalPage);
    }
}
