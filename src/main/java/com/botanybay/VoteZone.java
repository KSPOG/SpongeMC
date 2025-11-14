package com.botanybay;

import com.flowpowered.math.vector.Vector3i;
import java.util.UUID;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

/**
 * Represents an axis-aligned cuboid region that bounds the Botany Bay voting area.
 */
public final class VoteZone {

    private final UUID worldId;
    private final Vector3i min;
    private final Vector3i max;

    public VoteZone(final UUID worldId, final Vector3i min, final Vector3i max) {
        this.worldId = worldId;
        this.min = min;
        this.max = max;
    }

    public UUID getWorldId() {
        return worldId;
    }

    public Vector3i getMin() {
        return min;
    }

    public Vector3i getMax() {
        return max;
    }

    public boolean contains(final Location<World> location) {
        if (!location.getExtent().getUniqueId().equals(worldId)) {
            return false;
        }

        final Vector3i block = location.getBlockPosition();
        return block.getX() >= min.getX() && block.getX() <= max.getX()
                && block.getY() >= min.getY() && block.getY() <= max.getY()
                && block.getZ() >= min.getZ() && block.getZ() <= max.getZ();
    }
}
