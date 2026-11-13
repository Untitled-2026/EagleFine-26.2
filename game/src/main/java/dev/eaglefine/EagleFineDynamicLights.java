package dev.eaglefine;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Dynamic block lighting from light-emitting items held by nearby living
 * entities or dropped in the world.
 *
 * <p>The client tick builds an immutable light-field snapshot. Inline chunk
 * compilation and mesh workers read the same snapshot without touching the
 * live world. Light is propagated through blocks using each block state's
 * light dampening, and affected sections are invalidated when sources or
 * nearby block opacity change.</p>
 */
public final class EagleFineDynamicLights {
    private static final int MAX_LIGHT_LEVEL = 15;
    private static final int MAX_RADIUS = MAX_LIGHT_LEVEL - 1;
    private static final int MAX_ACTIVE_SOURCES = 64;
    private static final int MAX_LIGHT_CELLS = 131072;

    private static final int[] NEIGHBOR_X = { 1, -1, 0, 0, 0, 0 };
    private static final int[] NEIGHBOR_Y = { 0, 0, 1, -1, 0, 0 };
    private static final int[] NEIGHBOR_Z = { 0, 0, 0, 0, 1, -1 };

    private static final LightSource[] NO_SOURCES = new LightSource[0];
    private static final Long2ByteOpenHashMap NO_LIGHT_LEVELS = createEmptyLightField();

    private static volatile LightSource[] activeSources = NO_SOURCES;
    private static volatile Long2ByteOpenHashMap activeLightLevels = NO_LIGHT_LEVELS;

    private static ClientLevel trackedLevel;
    private static boolean lightFieldDirty;
    private static boolean hasDirtyBlockBounds;
    private static int dirtyMinX;
    private static int dirtyMinY;
    private static int dirtyMinZ;
    private static int dirtyMaxX;
    private static int dirtyMaxY;
    private static int dirtyMaxZ;

    private EagleFineDynamicLights() {
    }

    public static void tick(net.minecraft.client.Minecraft minecraft, EagleFineConfig config) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            trackedLevel = null;
            activeSources = NO_SOURCES;
            activeLightLevels = NO_LIGHT_LEVELS;
            lightFieldDirty = false;
            clearDirtyBlockBounds();
            return;
        }

        boolean changedLevel = trackedLevel != level;
        if (changedLevel) {
            trackedLevel = level;
            activeSources = NO_SOURCES;
            activeLightLevels = NO_LIGHT_LEVELS;
            lightFieldDirty = true;
            clearDirtyBlockBounds();
        }

        LocalPlayer player = minecraft.player;
        LightSource[] nextSources = config.isDynamicLightsEnabled() && player != null
                ? collectSources(level, player)
                : NO_SOURCES;

        LightSource[] previousSources = activeSources;
        if (!sameSources(previousSources, nextSources)) {
            if (!changedLevel) {
                markChangedSourceAreasDirty(level, previousSources, nextSources);
            }
            activeSources = nextSources;
            lightFieldDirty = true;
        }

        if (lightFieldDirty) {
            activeLightLevels = nextSources.length == 0
                    ? NO_LIGHT_LEVELS
                    : buildLightField(level, nextSources);

            if (hasDirtyBlockBounds) {
                markDirtyBlockArea(level);
            }
            lightFieldDirty = false;
            clearDirtyBlockBounds();
        }
    }

    /**
     * Notifies the light manager when a nearby block's light dampening changes.
     * The field is rebuilt on the next client tick, before the next render pass.
     */
    public static void onBlockChanged(
            ClientLevel level,
            BlockPos pos,
            BlockState oldState,
            BlockState newState) {
        if (trackedLevel != level || activeSources.length == 0
                || oldState.getLightDampening() == newState.getLightDampening()) {
            return;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (int i = 0; i < activeSources.length; ++i) {
            LightSource source = activeSources[i];
            int distance = Math.abs(x - source.x)
                    + Math.abs(y - source.y)
                    + Math.abs(z - source.z);
            if (distance <= MAX_RADIUS) {
                includeDirtyBlock(x, y, z);
                lightFieldDirty = true;
                return;
            }
        }
    }

    /** Returns the propagated dynamic block-light level at a block position. */
    public static int getLightLevel(int blockX, int blockY, int blockZ) {
        return activeLightLevels.get(BlockPos.asLong(blockX, blockY, blockZ)) & 0xFF;
    }

    private static LightSource[] collectSources(ClientLevel level, LocalPlayer player) {
        Map<Long, LightSource> byPosition = new HashMap<Long, LightSource>();
        addLivingEntitySources(byPosition, player);

        double range = MAX_RADIUS + 1.0D;
        AABB searchBounds = new AABB(
                player.getX() - range,
                player.getY() - range,
                player.getZ() - range,
                player.getX() + range,
                player.getEyeY() + range,
                player.getZ() + range);

        level.forEachEntityForRendering(searchBounds, entity -> {
            if (entity == player || entity.isRemoved()) {
                return;
            }

            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                int x = floor(itemEntity.getX());
                int y = floor(itemEntity.getY() + ItemEntity.EYE_HEIGHT);
                int z = floor(itemEntity.getZ());
                addItemSource(byPosition, stack, x, y, z);
            } else if (entity instanceof LivingEntity livingEntity) {
                addLivingEntitySources(byPosition, livingEntity);
            }
        });

        List<LightSource> result = new ArrayList<LightSource>(byPosition.values());
        final int playerX = floor(player.getX());
        final int playerY = floor(player.getEyeY() - 0.25D);
        final int playerZ = floor(player.getZ());
        Collections.sort(result, new Comparator<LightSource>() {
            @Override
            public int compare(LightSource first, LightSource second) {
                int firstDistance = distanceSquared(first, playerX, playerY, playerZ);
                int secondDistance = distanceSquared(second, playerX, playerY, playerZ);
                int byDistance = Integer.compare(firstDistance, secondDistance);
                if (byDistance != 0) {
                    return byDistance;
                }
                int byX = Integer.compare(first.x, second.x);
                if (byX != 0) {
                    return byX;
                }
                int byY = Integer.compare(first.y, second.y);
                return byY != 0 ? byY : Integer.compare(first.z, second.z);
            }
        });

        if (result.size() > MAX_ACTIVE_SOURCES) {
            result = new ArrayList<LightSource>(result.subList(0, MAX_ACTIVE_SOURCES));
        }
        return result.isEmpty() ? NO_SOURCES : result.toArray(new LightSource[result.size()]);
    }

    private static void addLivingEntitySources(Map<Long, LightSource> sources, LivingEntity entity) {
        int x = floor(entity.getX());
        int y = floor(entity.getEyeY() - 0.25D);
        int z = floor(entity.getZ());
        addItemSource(sources, entity.getMainHandItem(), x, y, z);
        addItemSource(sources, entity.getOffhandItem(), x, y, z);
    }

    private static void addItemSource(Map<Long, LightSource> sources, ItemStack stack, int x, int y, int z) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        int lightLevel = heldItemLightLevel(stack.getItem());
        if (lightLevel <= 0) {
            return;
        }

        long key = BlockPos.asLong(x, y, z);
        LightSource existing = sources.get(Long.valueOf(key));
        if (existing == null || lightLevel > existing.lightLevel) {
            sources.put(Long.valueOf(key), new LightSource(x, y, z, lightLevel));
        }
    }

    private static int heldItemLightLevel(Item item) {
        int level = 0;
        if (item instanceof BlockItem blockItem) {
            level = blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        if (item == Items.GLOW_BERRIES) {
            level = Math.max(level, 14);
        } else if (item == Items.LAVA_BUCKET) {
            level = Math.max(level, 15);
        }
        return Math.max(0, Math.min(MAX_LIGHT_LEVEL, level));
    }

    private static Long2ByteOpenHashMap buildLightField(ClientLevel level, LightSource[] sources) {
        Long2ByteOpenHashMap lightLevels = createEmptyLightField();
        ArrayDeque<LightNode> queue = new ArrayDeque<LightNode>();

        for (int i = 0; i < sources.length; ++i) {
            LightSource source = sources[i];
            long key = BlockPos.asLong(source.x, source.y, source.z);
            int previous = lightLevels.get(key) & 0xFF;
            if (source.lightLevel > previous) {
                lightLevels.put(key, (byte) source.lightLevel);
                queue.addLast(new LightNode(source.x, source.y, source.z, source.lightLevel));
            }
        }

        int minY = level.getMinY();
        int maxY = minY + level.getHeight();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty() && lightLevels.size() < MAX_LIGHT_CELLS) {
            LightNode current = queue.removeFirst();
            long currentKey = BlockPos.asLong(current.x, current.y, current.z);
            if ((lightLevels.get(currentKey) & 0xFF) != current.lightLevel) {
                continue;
            }

            for (int direction = 0; direction < NEIGHBOR_X.length; ++direction) {
                int x = current.x + NEIGHBOR_X[direction];
                int y = current.y + NEIGHBOR_Y[direction];
                int z = current.z + NEIGHBOR_Z[direction];
                if (y < minY || y >= maxY) {
                    continue;
                }

                neighborPos.set(x, y, z);
                BlockState state = level.getBlockState(neighborPos);
                int attenuation = Math.max(1, state.getLightDampening());
                int nextLevel = current.lightLevel - attenuation;
                if (nextLevel <= 0) {
                    continue;
                }

                long nextKey = BlockPos.asLong(x, y, z);
                int existingLevel = lightLevels.get(nextKey) & 0xFF;
                if (nextLevel > existingLevel) {
                    lightLevels.put(nextKey, (byte) nextLevel);
                    queue.addLast(new LightNode(x, y, z, nextLevel));
                }
            }
        }

        return lightLevels;
    }

    private static boolean sameSources(LightSource[] first, LightSource[] second) {
        if (first.length != second.length) {
            return false;
        }

        for (int i = 0; i < first.length; ++i) {
            if (!containsSource(second, first[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsSource(LightSource[] sources, LightSource target) {
        for (int i = 0; i < sources.length; ++i) {
            LightSource source = sources[i];
            if (source.x == target.x && source.y == target.y && source.z == target.z
                    && source.lightLevel == target.lightLevel) {
                return true;
            }
        }
        return false;
    }

    private static void markChangedSourceAreasDirty(
            ClientLevel level,
            LightSource[] previous,
            LightSource[] next) {
        for (int i = 0; i < previous.length; ++i) {
            if (!containsSource(next, previous[i])) {
                markSourceAreaDirty(level, previous[i]);
            }
        }
        for (int i = 0; i < next.length; ++i) {
            if (!containsSource(previous, next[i])) {
                markSourceAreaDirty(level, next[i]);
            }
        }
    }

    private static void markSourceAreaDirty(ClientLevel level, LightSource source) {
        level.setSectionRangeDirty(
                SectionPos.blockToSectionCoord(source.x - MAX_RADIUS),
                SectionPos.blockToSectionCoord(source.y - MAX_RADIUS),
                SectionPos.blockToSectionCoord(source.z - MAX_RADIUS),
                SectionPos.blockToSectionCoord(source.x + MAX_RADIUS),
                SectionPos.blockToSectionCoord(source.y + MAX_RADIUS),
                SectionPos.blockToSectionCoord(source.z + MAX_RADIUS));
    }

    private static void markDirtyBlockArea(ClientLevel level) {
        level.setSectionRangeDirty(
                SectionPos.blockToSectionCoord(dirtyMinX - MAX_RADIUS),
                SectionPos.blockToSectionCoord(dirtyMinY - MAX_RADIUS),
                SectionPos.blockToSectionCoord(dirtyMinZ - MAX_RADIUS),
                SectionPos.blockToSectionCoord(dirtyMaxX + MAX_RADIUS),
                SectionPos.blockToSectionCoord(dirtyMaxY + MAX_RADIUS),
                SectionPos.blockToSectionCoord(dirtyMaxZ + MAX_RADIUS));
    }

    private static void includeDirtyBlock(int x, int y, int z) {
        if (!hasDirtyBlockBounds) {
            dirtyMinX = dirtyMaxX = x;
            dirtyMinY = dirtyMaxY = y;
            dirtyMinZ = dirtyMaxZ = z;
            hasDirtyBlockBounds = true;
        } else {
            dirtyMinX = Math.min(dirtyMinX, x);
            dirtyMinY = Math.min(dirtyMinY, y);
            dirtyMinZ = Math.min(dirtyMinZ, z);
            dirtyMaxX = Math.max(dirtyMaxX, x);
            dirtyMaxY = Math.max(dirtyMaxY, y);
            dirtyMaxZ = Math.max(dirtyMaxZ, z);
        }
    }

    private static void clearDirtyBlockBounds() {
        hasDirtyBlockBounds = false;
        dirtyMinX = dirtyMinY = dirtyMinZ = 0;
        dirtyMaxX = dirtyMaxY = dirtyMaxZ = 0;
    }

    private static int distanceSquared(LightSource source, int x, int y, int z) {
        int dx = source.x - x;
        int dy = source.y - y;
        int dz = source.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static Long2ByteOpenHashMap createEmptyLightField() {
        Long2ByteOpenHashMap result = new Long2ByteOpenHashMap();
        result.defaultReturnValue((byte) 0);
        return result;
    }

    private static final class LightNode {
        private final int x;
        private final int y;
        private final int z;
        private final int lightLevel;

        private LightNode(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }

    private static final class LightSource {
        private final int x;
        private final int y;
        private final int z;
        private final int lightLevel;

        private LightSource(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }
}
