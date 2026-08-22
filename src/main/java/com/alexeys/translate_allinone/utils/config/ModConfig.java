package com.alexeys.translate_allinone.utils.config;

import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.alexeys.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.alexeys.translate_allinone.utils.config.pojos.DebugConfig;
import com.alexeys.translate_allinone.utils.config.pojos.DictionaryConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.google.gson.annotations.SerializedName;

public class ModConfig {
    @SerializedName(value = "chatTranslate", alternate = {"chatTranslateConfig", "ChatTranslateConfig"})
    public ChatTranslateConfig chatTranslate = new ChatTranslateConfig();

    @SerializedName(value = "itemTranslate", alternate = {"itemTranslateConfig", "ItemTranslateConfig"})
    public ItemTranslateConfig itemTranslate = new ItemTranslateConfig();

    @SerializedName(value = "scoreboardTranslate", alternate = {"scoreboardConfig", "ScoreboardConfig"})
    public ScoreboardConfig scoreboardTranslate = new ScoreboardConfig();

    public OtherTranslationsConfig otherTranslations = new OtherTranslationsConfig();

    public WynnCraftConfig wynnCraft = new WynnCraftConfig();

    public DictionaryConfig dictionary = new DictionaryConfig();

    public CacheBackupConfig cacheBackup = new CacheBackupConfig();

    public DebugConfig debug = new DebugConfig();

    public ProviderManagerConfig providerManager = new ProviderManagerConfig();
}
