package com.enviouse.sef.banned;

/**
 * A block that is excepted from banned-block sweeps.
 *
 * <p>Auto-recorded when a player in creative mode places a banned block.
 * Persists across restarts. Listed by {@code /banned excepted} with a
 * click-to-tp link.
 */
public class BannedExceptedBlock {
    /** Registry id of the dimension (e.g. {@code minecraft:overworld}). */
    public String dimension;
    public int x;
    public int y;
    public int z;
    /** Registry id of the banned block at the time of placement. */
    public String itemId = "";
    /** Player who placed it. */
    public String addedBy = "";
    public long addedAtMillis = 0L;

    public BannedExceptedBlock() {}

    public BannedExceptedBlock(String dimension, int x, int y, int z,
                               String itemId, String addedBy) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemId = itemId == null ? "" : itemId;
        this.addedBy = addedBy == null ? "" : addedBy;
        this.addedAtMillis = System.currentTimeMillis();
    }

    public boolean matches(String dim, int bx, int by, int bz) {
        return bx == x && by == y && bz == z
                && dim != null && dim.equals(dimension);
    }
}
