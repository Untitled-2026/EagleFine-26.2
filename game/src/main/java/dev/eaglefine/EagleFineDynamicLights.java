package dev.eaglefine;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Dynamic block lighting from emissive items, displayed blocks, glowing or
 * burning entities, primed TNT, and lightning in the nearby client world.
 *
 * <p>The light field is rebuilt on the client thread and then published as an
 * immutable snapshot. Inline chunk compilation and mesh workers read that
 * same snapshot without querying the live entity list. There are no source
 * count or visited-cell caps: the only natural reach limit is Minecraft's
 * 15-level block-light falloff.</p>
 */
public final class EagleFineDynamicLights {
    private static final int MAX_LIGHT_LEVEL = 15;
    private static final int MAX_RADIUS = MAX_LIGHT_LEVEL - 1;

    private static final int[] NEIGHBOR_X = { 1, -1, 0, 0, 0, 0 };
    private static final int[] NEIGHBOR_Y = { 0, 0, 1, -1, 0, 0 };
    private static final int[] NEIGHBOR_Z = { 0, 0, 0, 0, 1, -1 };

    private static final LightSource[] NO_SOURCES = new LightSource[0];
    private static final Long2ByteOpenHashMap NO_LIGHT_LEVELS = createEmptyLightField();

    private static volatile LightSource[] activeSources = NO_SOURCES;
    private static volatile Long2ByteOpenHashMap activeLightLevels = NO_LIGHT_LEVELS;
    private static volatile long lastFieldBuildNanos;
    private static volatile int lastFieldCellCount;
    private static volatile int lastFieldSourceCount;

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

    public static void tick(Minecraft minecraft, EagleFineConfig config) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            trackedLevel = null;
            activeSources = NO_SOURCES;
            activeLightLevels = NO_LIGHT_LEVELS;
            lightFieldDirty = false;
            lastFieldSourceCount = 0;
            lastFieldCellCount = 0;
            lastFieldBuildNanos = 0L;
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
                markChangedSourceAreasDirty(previousSources, nextSources);
            }
            activeSources = nextSources;
            lightFieldDirty = true;
        }

        if (lightFieldDirty) {
            if (nextSources.length == 0) {
                activeLightLevels = NO_LIGHT_LEVELS;
                lastFieldSourceCount = 0;
                lastFieldCellCount = 0;
                lastFieldBuildNanos = 0L;
            } else {
                activeLightLevels = buildLightField(level, nextSources);
            }

            if (hasDirtyBlockBounds) {
                markDirtyBlockArea(level);
            }
            lightFieldDirty = false;
            clearDirtyBlockBounds();
        }
    }

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

    public static int getLightLevel(int blockX, int blockY, int blockZ) {
        return activeLightLevels.get(BlockPos.asLong(blockX, blockY, blockZ)) & 0xFF;
    }

    public static int getLastFieldSourceCount() {
        return lastFieldSourceCount;
    }

    public static int getLastFieldCellCount() {
        return lastFieldCellCount;
    }

    public static long getLastFieldBuildNanos() {
        return lastFieldBuildNanos;
    }

    private static LightSource[] collectSources(ClientLevel level, LocalPlayer player) {
        Map<Long, LightSource> byPosition = new HashMap<Long, LightSource>();
        addLivingEntitySources(byPosition, player);

        // A level-15 source loses at least one light level per block, so
        // sources farther than 14 blocks cannot contribute to this player.
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
                addItemSource(
                        byPosition,
                        itemEntity.getItem(),
                        floor(itemEntity.getX()),
                        floor(itemEntity.getY() + ItemEntity.EYE_HEIGHT),
                        floor(itemEntity.getZ()));
            } else if (entity instanceof LivingEntity livingEntity) {
                addLivingEntitySources(byPosition, livingEntity);
            } else if (entity instanceof ItemFrame itemFrame) {
                addItemSource(
                        byPosition,
                        itemFrame.getItem(),
                        floor(itemFrame.getX()),
                        floor(itemFrame.getY()),
                        floor(itemFrame.getZ()));
            } else if (entity instanceof Display.ItemDisplay itemDisplay) {
                addItemSource(
                        byPosition,
                        itemDisplay.getItemStack(),
                        floor(itemDisplay.getX()),
                        floor(itemDisplay.getY()),
                        floor(itemDisplay.getZ()));
            } else if (entity instanceof Display.BlockDisplay blockDisplay) {
                addBlockStateSource(
                        byPosition,
                        blockDisplay.getBlockState(),
                        floor(blockDisplay.getX()),
                        floor(blockDisplay.getY()),
                        floor(blockDisplay.getZ()));
            } else if (entity instanceof FallingBlockEntity fallingBlock) {
                addBlockStateSource(
                        byPosition,
                        fallingBlock.getBlockState(),
                        floor(fallingBlock.getX()),
                        floor(fallingBlock.getY()),
                        floor(fallingBlock.getZ()));
            }

            if (entity instanceof GlowItemFrame) {
                addSource(byPosition, floor(entity.getX()), floor(entity.getY()), floor(entity.getZ()), 8);
            }

            if (entity.isCurrentlyGlowing()) {
                addSource(
                        byPosition,
                        floor(entity.getX()),
                        floor(entity.getY() + entity.getBbHeight() * 0.5D),
                        floor(entity.getZ()),
                        8);
            }

            // This includes burning mobs, burning projectiles, and charged
            // Blazes (whose isOnFire implementation reflects their charge).
            if (entity.isOnFire()) {
                addSource(
                        byPosition,
                        floor(entity.getX()),
                        floor(entity.getY() + entity.getBbHeight() * 0.5D),
                        floor(entity.getZ()),
                        MAX_LIGHT_LEVEL);
            }

            if (entity instanceof PrimedTnt || entity instanceof LightningBolt) {
                addSource(byPosition, floor(entity.getX()), floor(entity.getY()), floor(entity.getZ()), MAX_LIGHT_LEVEL);
            }
        });

        return byPosition.isEmpty()
                ? NO_SOURCES
                : byPosition.values().toArray(new LightSource[byPosition.size()]);
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
        addSource(sources, x, y, z, heldItemLightLevel(stack.getItem()));
    }

    private static void addBlockStateSource(Map<Long, LightSource> sources, BlockState state, int x, int y, int z) {
        if (state != null && !state.isAir()) {
            addSource(sources, x, y, z, state.getLightEmission());
        }
    }

    private static void addSource(Map<Long, LightSource> sources, int x, int y, int z, int lightLevel) {
        int level = Math.max(0, Math.min(MAX_LIGHT_LEVEL, lightLevel));
        if (level <= 0) {
            return;
        }

        long key = BlockPos.asLong(x, y, z);
        LightSource existing = sources.get(Long.valueOf(key));
        if (existing == null || level > existing.lightLevel) {
            sources.put(Long.valueOf(key), new LightSource(x, y, z, level));
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
            level = Math.max(level, MAX_LIGHT_LEVEL);
        }
        return Math.max(0, Math.min(MAX_LIGHT_LEVEL, level));
    }

    private static Long2ByteOpenHashMap buildLightField(ClientLevel level, LightSource[] sources) {
        long started = System.nanoTime();
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

        // No arbitrary work or cell-count cutoff: propagation ends naturally
        // when every path has attenuated to block-light level zero.
        while (!queue.isEmpty()) {
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

        lastFieldBuildNanos = System.nanoTime() - started;
        lastFieldCellCount = lightLevels.size();
        lastFieldSourceCount = sources.length;
        return lightLevels;
    }

    private static boolean sameSources(LightSource[] first, LightSource[] second) {
        if (first.length != second.length) {
            return false;
        }

        Long2ByteOpenHashMap secondByPosition = sourceLevels(second);
        for (int i = 0; i < first.length; ++i) {
            LightSource source = first[i];
            int level = secondByPosition.get(BlockPos.asLong(source.x, source.y, source.z)) & 0xFF;
            if (level != source.lightLevel) {
                return false;
            }
        }
        return true;
    }

    private static Long2ByteOpenHashMap sourceLevels(LightSource[] sources) {
        Long2ByteOpenHashMap result = createEmptyLightField();
        for (int i = 0; i < sources.length; ++i) {
            LightSource source = sources[i];
            result.put(BlockPos.asLong(source.x, source.y, source.z), (byte) source.lightLevel);
        }
        return result;
    }

    private static void markChangedSourceAreasDirty(LightSource[] previous, LightSource[] next) {
        Long2ByteOpenHashMap previousByPosition = sourceLevels(previous);
        Long2ByteOpenHashMap nextByPosition = sourceLevels(next);

        for (int i = 0; i < previous.length; ++i) {
            LightSource source = previous[i];
            int nextLevel = nextByPosition.get(BlockPos.asLong(source.x, source.y, source.z)) & 0xFF;
            if (nextLevel != source.lightLevel) {
                includeDirtyBlock(source.x, source.y, source.z);
            }
        }

        for (int i = 0; i < next.length; ++i) {
            LightSource source = next[i];
            int previousLevel = previousByPosition.get(BlockPos.asLong(source.x, source.y, source.z)) & 0xFF;
            if (previousLevel != source.lightLevel) {
                includeDirtyBlock(source.x, source.y, source.z);
            }
        }
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
