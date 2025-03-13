package io.wispforest.lavender;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.wispforest.endec.Endec;
import io.wispforest.endec.impl.BuiltInEndecs;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.lavender.book.Entry;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.owo.serialization.CodecUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "lavender";
    public static final SoundEvent ITEM_BOOK_OPEN = SoundEvent.of(id("item.book.open"));

    private static final String BIOMES_FILE_PATH = "biomes.json";

    private static final Gson GSON = new Gson();

    private Set<String> obtainedBiomes = new HashSet<>();

    private int tickCounter = 0;

    private File biomesFile;

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("dynamic_book"), LavenderBookItem.DYNAMIC_BOOK);
        Registry.register(Registries.SOUND_EVENT, ITEM_BOOK_OPEN.getId(), ITEM_BOOK_OPEN);

        PayloadTypeRegistry.playS2C().register(WorldUUIDPayload.ID, CodecUtils.toPacketCodec(WorldUUIDPayload.ENDEC));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new WorldUUIDPayload(server.getOverworld().getPersistentStateManager().getOrCreate(WorldUUIDState.TYPE, "lavender_world_id").id));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }


    private void onServerStarted(MinecraftServer server) {
        Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
        Set<String> currentBiomes = biomeRegistry.getIds().stream().map(Identifier::toString).collect(Collectors.toSet());
        String worldName = server.getSaveProperties().getLevelName();
        this.biomesFile = new File("biomes_" + worldName + ".json");
        JsonObject biomesJson = new JsonObject();
        JsonArray potentialBiomes = new JsonArray();
        JsonArray obtainedBiomesJson = new JsonArray();
        Objects.requireNonNull(potentialBiomes);
        currentBiomes.forEach(potentialBiomes::add);
        Objects.requireNonNull(obtainedBiomesJson);
        this.obtainedBiomes.forEach(obtainedBiomesJson::add);
        biomesJson.add("potential", (JsonElement) potentialBiomes);
        biomesJson.add("obtained", (JsonElement) obtainedBiomesJson);
        if (this.biomesFile.exists()) {
            try {
                String existingContent = new String(Files.readAllBytes(Paths.get(this.biomesFile.getPath())));
                JsonObject existingJson = GSON.fromJson(existingContent, JsonObject.class);
                JsonArray existingBiomesArray = existingJson.getAsJsonArray("potential");
                Set<String> existingBiomes = new HashSet<>();
                existingBiomesArray.forEach(element -> existingBiomes.add(element.getAsString()));
                if (!currentBiomes.equals(existingBiomes))
                    writeBiomesToFile(biomesJson);
            } catch (IOException e) {
                LOGGER.warn(e.toString());
            }
        } else {
            writeBiomesToFile(biomesJson);
        }
        Entry.setBiomesFilePath(this.biomesFile.getPath());
    }

    private void onServerTick(MinecraftServer server) {
        this.tickCounter++;
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Biome biome = player.getWorld().getBiome(player.getBlockPos()).value();
                String biomeId = String.valueOf(server.getRegistryManager().get(RegistryKeys.BIOME).getId(biome));
                if (this.obtainedBiomes.add(biomeId))
                    updateObtainedBiomesFile();
            }
        }
    }

    private void updateObtainedBiomesFile() {
        try {
            String existingContent = new String(Files.readAllBytes(Paths.get(this.biomesFile.getPath(), new String[0])));
            JsonObject biomesJson = (JsonObject)GSON.fromJson(existingContent, JsonObject.class);
            JsonArray obtainedBiomesJson = new JsonArray();
            Objects.requireNonNull(obtainedBiomesJson);
            this.obtainedBiomes.forEach(obtainedBiomesJson::add);
            biomesJson.add("obtained", (JsonElement)obtainedBiomesJson);
            writeBiomesToFile(biomesJson);
        } catch (IOException e) {
            LOGGER.warn(e.toString());
        }
    }

    private void writeBiomesToFile(JsonObject biomesJson) {
        try {
            FileWriter writer = new FileWriter(this.biomesFile);
            try {
                GSON.toJson((JsonElement)biomesJson, writer);
                writer.close();
            } catch (Throwable throwable) {
                try {
                    writer.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (IOException e) {
            LOGGER.warn(e.toString());
        }
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static class WorldUUIDState extends PersistentState {

        public static final PersistentState.Type<WorldUUIDState> TYPE = new Type<>(() -> {
            var state = new WorldUUIDState(UUID.randomUUID());
            state.markDirty();
            return state;
        }, WorldUUIDState::read, DataFixTypes.LEVEL);

        public final UUID id;

        private WorldUUIDState(UUID id) {
            this.id = id;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
            nbt.putUuid("UUID", id);
            return nbt;
        }

        public static WorldUUIDState read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            return new WorldUUIDState(nbt.contains("UUID", NbtElement.INT_ARRAY_TYPE) ? nbt.getUuid("UUID") : null);
        }
    }

    public record WorldUUIDPayload(UUID worldUuid) implements CustomPayload {
        public static final CustomPayload.Id<WorldUUIDPayload> ID = new CustomPayload.Id<>(Lavender.id("world_uuid"));
        public static final Endec<WorldUUIDPayload> ENDEC = StructEndecBuilder.of(
                BuiltInEndecs.UUID.fieldOf("world_uuid", WorldUUIDPayload::worldUuid),
                WorldUUIDPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
