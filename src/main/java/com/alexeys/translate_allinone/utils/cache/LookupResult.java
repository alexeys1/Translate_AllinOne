package com.alexeys.translate_allinone.utils.cache;

public record LookupResult(TranslationStatus status, String translation, String errorMessage) {}
