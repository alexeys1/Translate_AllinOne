package com.alexeys.translate_allinone.gui.configui.model;

import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

public record ParameterTreeRow(String path, CustomParameterEntry entry, int depth) {
}
