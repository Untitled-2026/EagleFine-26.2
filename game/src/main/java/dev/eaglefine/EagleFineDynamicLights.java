package dev.eaglefine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * First-pass Dynamic Lights implementation for light-emitting items held by
 * the local player.
 *
 * <p>Light sources are sampled on the client tick and published as an
 * immutable snapshot so inline and mesh-worker section compilers can read
 * them safely. Changed source positions invalidate the affected chunk sections.</p>
 */
public final class EagleFineDynamicLights {
    private static final int MAX_LIGHT_LEVEL = 15;
    private static final int MAX_RADIUS = MAX_LIGHT_LEVEL - 1;
    private static final LightSource[] NO_SOURCES = new LightSource[0];

    private static volatile LightSource[] activeSources = NO_SOURCES;
    private static ClientLevel trackedLevel;

    private EagleFineDynamicLights() {
    }

    public static void tick(Minecraft minecraft, EagleFineConfig config) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            trackedLevel = null;
            activeSources = NO_SOURCES;
            return;
        }

        boolean changedLevel = trackedLevel != level;
        if (changedLevel) {
            trackedLevel = level;
            activeSources = NO_SOURCES;
        }

        LocalPlayer player = minecraft.player;
        LightSource[] nextSources = config.isDynamicLightsEnabled() && player != null
                ? collectHeldSources(player)
                : NO_SOURCES;

        LightSource[] previousSources = activeSources;
        if (!sameSources(previousSources, nextSources)) {
            if (!changedLevel) {
                markAffectedSectionsDirty(level, previousSources);
            }
            markAffectedSectionsDirty(level, nextSources);
            activeSources = nextSources;
        }
    }

    /** Returns the distance-attenuated dynamic block-light level at a position. */
    public static int getLightLevel(int blockX, int blockY, int blockZ) {
        int result = 0;
        LightSource[] sources = activeSources;
        for (int i = 0; i < sources.length; ++i) {
            LightSource source = sources[i];
            int dx = blockX - source.x;
            int dy = blockY - source.y;
            int dz = blockZ - source.z;
            int distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > MAX_RADIUS * MAX_RADIUS) {
                continue;
            }

            int distance = (int)Math.floor(Math.sqrt(distanceSquared));
            int level = source.lightLevel - distance;
            if (level > result) {
                result = level;
                if (result >= MAX_LIGHT_LEVEL) {
                    return MAX_LIGHT_LEVEL;
                }
            }
        }
        return result;
    }

    private static LightSource[] collectHeldSources(LocalPlayer player) {
        List<LightSource> result = new ArrayList<LightSource>(2);
        int x = (int)Math.floor(player.getX());
        int y = (int)Math.floor(player.getEyeY() - 0.25D);
        int z = (int)Math.floor(player.getZ());

        addHeldSource(result, player.getMainHandItem(), x, y, z);
        addHeldSource(result, player.getOffhandItem(), x, y, z);
        return result.isEmpty() ? NO_SOURCES : result.toArray(new LightSource[result.size()]);
    }

    private static void addHeldSource(List<LightSource> sources, ItemStack stack, int x, int y, int z) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        int level = heldItemLightLevel(stack.getItem());
        if (level <= 0) {
            return;
        }

        for (int i = 0; i < sources.size(); ++i) {
            LightSource source = sources.get(i);
            if (source.x == x && source.y == y && source.z == z) {
                if (level > source.lightLevel) {
                    sources.set(i, new LightSource(x, y, z, level));
                }
                return;
            }
        }
        sources.add(new LightSource(x, y, z, level));
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

    private static boolean sameSources(LightSource[] first, LightSource[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; ++i) {
            LightSource a = first[i];
            LightSource b = second[i];
            if (a.x != b.x || a.y != b.y || a.z != b.z || a.lightLevel != b.lightLevel) {
                return false;
            }
        }
        return true;
    }

    private static void markAffectedSectionsDirty(ClientLevel level, LightSource[] sources) {
        for (int i = 0; i < sources.length; ++i) {
            LightSource source = sources[i];
            int minX = SectionPos.blockToSectionCoord(source.x - MAX_RADIUS);
            int minY = SectionPos.blockToSectionCoord(source.y - MAX_RADIUS);
            int minZ = SectionPos.blockToSectionCoord(source.z - MAX_RADIUS);
            int maxX = SectionPos.blockToSectionCoord(source.x + MAX_RADIUS);
            int maxY = SectionPos.blockToSectionCoord(source.y + MAX_RADIUS);
            int maxZ = SectionPos.blockToSectionCoord(source.z + MAX_RADIUS);
            level.setSectionRangeDirty(minX, minY, minZ, maxX, maxY, maxZ);
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
