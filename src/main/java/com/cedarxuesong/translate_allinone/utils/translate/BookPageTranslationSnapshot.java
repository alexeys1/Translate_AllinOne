package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import net.minecraft.text.Text;

public record BookPageTranslationSnapshot(
        int pageIndex,
        Text originalPage,
        Text displayedPage,
        ComponentTranslationRuntime.State state
) {
    public boolean isTranslated() {
        return state == ComponentTranslationRuntime.State.CACHE_HIT
                && displayedPage != null
                && !displayedPage.equals(originalPage);
    }
}
