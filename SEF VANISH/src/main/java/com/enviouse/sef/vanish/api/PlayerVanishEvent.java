package com.enviouse.sef.vanish.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class PlayerVanishEvent extends PlayerEvent {
	private final boolean vanished;
	private final int vanishLevel;

	public PlayerVanishEvent(Player player, boolean vanished, int vanishLevel) {
		super(player);
		this.vanished = vanished;
		this.vanishLevel = vanishLevel;
	}

	public boolean isVanished() {
		return vanished;
	}

	/** Returns the vanish level (1=highest/most hidden, 2, 3=lowest). 0 if unvanishing. */
	public int getVanishLevel() {
		return vanishLevel;
	}
}
