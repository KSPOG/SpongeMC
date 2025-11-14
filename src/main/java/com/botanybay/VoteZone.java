package com.botanybay;


import java.util.UUID;

import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.math.vector.Vector3i;


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
        return this.worldId;
    }

    public Vector3i getMin() {
        return this.min;
    }

    public Vector3i getMax() {
        return this.max;
    }

    public boolean contains(final ServerLocation location) {
        if (!location.world().uniqueId().equals(this.worldId)) {
            return false;
        }

        final Vector3i block = location.blockPosition();
        return block.x() >= this.min.x() && block.x() <= this.max.x()
                && block.y() >= this.min.y() && block.y() <= this.max.y()
                && block.z() >= this.min.z() && block.z() <= this.max.z();


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
