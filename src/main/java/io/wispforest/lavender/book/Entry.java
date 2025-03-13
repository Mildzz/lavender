package io.wispforest.lavender.book;

import com.google.common.collect.ImmutableSet;
import com.google.gson.*;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.mixin.access.ClientAdvancementManagerAccessor;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public record Entry(
        Identifier id,
        List<Identifier> categories,
        String title,
        Function<Sizing, Component> iconFactory,
        boolean secret,
        int ordinal,
        ImmutableSet<Identifier> requiredAdvancements,
        ImmutableSet<ItemStack> associatedItems,
        ImmutableSet<Identifier> requiredBiomes,
        ImmutableSet<String> additionalSearchTerms,
        String content
) implements Book.BookmarkableElement {
    private static final Gson GSON = new Gson();
    private static String biomesFilePath;

    public static void setBiomesFilePath(String path) {
        biomesFilePath = path;
    }

    public boolean canPlayerView(ClientPlayerEntity player) {
        var advancementHandler = player.networkHandler.getAdvancementHandler();

        for (var advancementId : this.requiredAdvancements) {
            var advancement = advancementHandler.getManager().get(advancementId);
            if (advancement == null) return false;

            var progress = ((ClientAdvancementManagerAccessor) advancementHandler).lavender$getAdvancementProgresses().get(advancement.getAdvancementEntry());
            if (progress == null || !progress.isDone()) return false;
        }

        if (biomesFilePath != null) {
            try (FileReader reader = new FileReader(biomesFilePath)) {
                JsonObject biomesJson = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray potentialBiomes = biomesJson.getAsJsonArray("potential");
                JsonArray obtainedBiomes = biomesJson.getAsJsonArray("obtained");

                Set<String> potentialBiomesSet = jsonArrayToSet(potentialBiomes);
                Set<String> obtainedBiomesSet = jsonArrayToSet(obtainedBiomes);

                for (Identifier requiredBiome : this.requiredBiomes) {
                    String biomeId = requiredBiome.toString();
                    if (potentialBiomesSet.contains(biomeId) && !obtainedBiomesSet.contains(biomeId)) {
                        return false;
                    }
                }
            } catch (IOException e) {
                Lavender.LOGGER.warn(e.toString());
                return false;
            }
        }

        return true;
    }

    private Set<String> potentialBiomesToSet(JsonArray potentialBiomes) {
        return jsonArrayToSet(potentialBiomes);
    }

    private Set<String> obtainedBiomesToSet(JsonArray obtainedBiomes) {
        return jsonArrayToSet(obtainedBiomes);
    }

    private Set<String> jsonArrayToSet(JsonArray jsonArray) {
        return StreamSupport.stream(jsonArray.spliterator(), false)
                .map(JsonElement::getAsString)
                .collect(Collectors.toSet());
    }
}
