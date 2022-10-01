package net.caffeinemc.sodium.config.user;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.function.Predicate;
import net.caffeinemc.gfx.api.device.RenderDeviceProperties;
import net.caffeinemc.sodium.config.user.options.TextProvider;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.text.Text;

public class UserConfig {
    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final NotificationSettings notifications = new NotificationSettings();

    private Path configPath;

    public static UserConfig defaults(Path path) {
        var options = new UserConfig();
        options.configPath = path;
        options.sanitize();

        return options;
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        public boolean alwaysDeferChunkUpdates = false;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useParticleCulling = true;
        public boolean useBlockFaceCulling = true;
        public boolean useCompactVertexFormat = true;
        public boolean useModelInstancing = true;
    }

    public static class AdvancedSettings {
        public int cpuRenderAheadLimit = 3;

        public boolean allowDirectMemoryAccess = true;
        public boolean enableMemoryTracing = false;
        public boolean enableApiDebug = false;
        
        //public ChunkRendererBackend chunkRendererBackend = ChunkRendererBackend.DEFAULT;
        public ChunkRendererBackend chunkRendererBackend = ChunkRendererBackend.GPU_DRIVEN;
        public RegionDataStore regionDataStore = RegionDataStore.DEFAULT;
    }

    public static class QualitySettings {
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;
        
        public boolean useTranslucentFaceSorting = true;

        public boolean enableVignette = true;
    }

    public static class NotificationSettings {
        public boolean hideDonationButton = false;
    }

    public enum ChunkRendererBackend implements TextProvider {
        DEFAULT("options.gamma.default", p -> true),
        BASEVERTEX("sodium.options.chunk_renderer_backend.base_vertex", p -> true),
        INDIRECT("sodium.options.chunk_renderer_backend.indirect", p -> true),
        GPU_DRIVEN("sodium.options.chunk_renderer_backend.gpu", p -> true),
        VULKAN("sodium.options.chunk_renderer_backend.vulkan", p -> true);

        private final Text name;
        private final Predicate<RenderDeviceProperties> supportedSupplier;

        ChunkRendererBackend(String name, Predicate<RenderDeviceProperties> supportedSupplier) {
            this.name = Text.translatable(name);
            this.supportedSupplier = supportedSupplier;
        }

        @Override
        public Text getLocalizedName() {
            return this.name;
        }

        public boolean isSupported(RenderDeviceProperties deviceProperties) {
            return this.supportedSupplier.test(deviceProperties);
        }

        public static ChunkRendererBackend[] getSupportedValues(RenderDeviceProperties deviceProperties) {
            return Arrays.stream(ChunkRendererBackend.values())
                    .filter(tdm -> tdm.isSupported(deviceProperties))
                    .toArray(ChunkRendererBackend[]::new);
        }
    }


    public enum RegionDataStore implements TextProvider {
        DEFAULT("options.gamma.default", p -> true),
        SPARSE("sodium.options.gpu_occlusion.region_store.sparse", p -> true),
        SINGLE("sodium.options.gpu_occlusion.region_store.single", p -> true),
        DISTINCT("sodium.options.gpu_occlusion.region_store.distinct", p -> true);

        private final Text name;
        private final Predicate<RenderDeviceProperties> supportedSupplier;

        RegionDataStore(String name, Predicate<RenderDeviceProperties> supportedSupplier) {
            this.name = Text.translatable(name);
            this.supportedSupplier = supportedSupplier;
        }

        @Override
        public Text getLocalizedName() {
            return this.name;
        }

        public boolean isSupported(RenderDeviceProperties deviceProperties) {
            return this.supportedSupplier.test(deviceProperties);
        }

        public static RegionDataStore[] getSupportedValues(RenderDeviceProperties deviceProperties) {
            return Arrays.stream(RegionDataStore.values())
                    .filter(tdm -> tdm.isSupported(deviceProperties))
                    .toArray(RegionDataStore[]::new);
        }
    }

    public enum GraphicsQuality implements TextProvider {
        DEFAULT("options.gamma.default"),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        private final Text name;

        GraphicsQuality(String name) {
            this.name = Text.translatable(name);
        }

        @Override
        public Text getLocalizedName() {
            return this.name;
        }

        public boolean isFancy(GraphicsMode graphicsMode) {
            return (this == FANCY) || (this == DEFAULT && (graphicsMode == GraphicsMode.FANCY || graphicsMode == GraphicsMode.FABULOUS));
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static UserConfig load(Path path) {
        UserConfig config;

        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                config = GSON.fromJson(reader, UserConfig.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            }

            config.configPath = path;
            config.sanitize();
        } else {
            config = UserConfig.defaults(path);
        }

        try {
            config.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't update config file", e);
        }

        return config;
    }

    private void sanitize() {
        // NOOP for now
    }

    public void writeChanges() throws IOException {
        Path dir = this.configPath.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        // Use a temporary location next to the config's final destination
        Path tempPath = this.configPath.resolveSibling(this.configPath.getFileName() + ".tmp");

        // Write the file to our temporary location
        Files.writeString(tempPath, GSON.toJson(this));

        // Atomically replace the old config file (if it exists) with the temporary file
        Files.move(tempPath, this.configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
