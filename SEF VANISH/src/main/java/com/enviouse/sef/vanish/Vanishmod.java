package com.enviouse.sef.vanish;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.server.permission.nodes.PermissionNode;

/**
 * Vanish system data holder — no longer a standalone @Mod.
 * Registration of commands, config, and permissions is done in {@link com.enviouse.sef.ServerEssentialsForge}.
 */
public class Vanishmod {
	/** Permission nodes for vanishsee levels 1-3: sef.vanishsee.N. Key = level, Value = node. */
	public static final Map<Integer, PermissionNode<Boolean>> VANISH_SEE_NODES = new HashMap<>();

	/** Permission nodes for vanish levels 1-3: sef.vanish.N. Key = level, Value = node. */
	public static final Map<Integer, PermissionNode<Boolean>> VANISH_LEVEL_NODES = new HashMap<>();
}
