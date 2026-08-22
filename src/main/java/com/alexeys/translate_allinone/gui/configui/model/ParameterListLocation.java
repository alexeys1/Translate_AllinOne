package com.alexeys.translate_allinone.gui.configui.model;

import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.List;

public record ParameterListLocation(List<CustomParameterEntry> list, int index) {
}
