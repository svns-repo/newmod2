package com.example.blockesp;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.loader.api.FabricLoader;

/** All mod settings. Saved to .minecraft/config/blockesp.json */
public final class EspConfig {
	public enum Category {
		// ---- Storage & spawners (found via block entities) ----
		CHESTS("Chests", 0xFFD700, true, null),
		TRAPPED_CHESTS("Trapped Chests", 0xFF5050, true, null),
		BARRELS("Barrels", 0xA0522D, true, null),
		SHULKER_BOXES("Shulker Boxes", 0xB050FF, true, null),
		ENDER_CHESTS("Ender Chests", 0x20C0A0, true, null),
		SPAWNERS("Spawners", 0xFF2020, true, null),
		TRIAL_SPAWNERS("Trial Spawners", 0xFF8C00, true, null),
		HOPPERS("Hoppers", 0x808080, false, null),
		DISPENSERS("Dispensers", 0x6080FF, false, null),
		FURNACES("Furnaces", 0xE0E0E0, false, null),

		// ---- Ores (found by scanning block states) ----
		DIAMOND_ORE("Diamond", 0x00FFFF, true, s -> s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)),
		ANCIENT_DEBRIS("Ancient Debris", 0xC06030, true, s -> s.is(Blocks.ANCIENT_DEBRIS)),
		EMERALD_ORE("Emerald", 0x00FF60, false, s -> s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE)),
		GOLD_ORE("Gold", 0xFFAA00, false, s -> s.is(BlockTags.GOLD_ORES)),
		IRON_ORE("Iron", 0xD8AF93, false, s -> s.is(BlockTags.IRON_ORES)),
		REDSTONE_ORE("Redstone", 0xFF0000, false, s -> s.is(Blocks.REDSTONE_ORE) || s.is(Blocks.DEEPSLATE_REDSTONE_ORE)),
		LAPIS_ORE("Lapis", 0x2050FF, false, s -> s.is(Blocks.LAPIS_ORE) || s.is(Blocks.DEEPSLATE_LAPIS_ORE)),
		COPPER_ORE("Copper", 0xE07040, false, s -> s.is(BlockTags.COPPER_ORES)),
		COAL_ORE("Coal", 0x404040, false, s -> s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_COAL_ORE)),
		QUARTZ_ORE("Nether Quartz", 0xF0F0F0, false, s -> s.is(Blocks.NETHER_QUARTZ_ORE));

		public final String label;
		public final int rgb;
		public final boolean defaultOn;
		/** Non-null for ores: tests whether a block state belongs to this category. */
		public final Predicate<BlockState> oreMatcher;

		Category(String label, int rgb, boolean defaultOn, Predicate<BlockState> oreMatcher) {
			this.label = label;
			this.rgb = rgb;
			this.defaultOn = defaultOn;
			this.oreMatcher = oreMatcher;
		}

		public boolean isOre() {
			return oreMatcher != null;
		}
	}

	private static final String KEY_TOGGLE_MESSAGE = "SHOW_TOGGLE_MESSAGE";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blockesp.json");
	private static final EnumMap<Category, Boolean> ENABLED = new EnumMap<>(Category.class);
	private static boolean showToggleMessage = true;

	private EspConfig() {}

	public static boolean isEnabled(Category c) {
		return ENABLED.getOrDefault(c, c.defaultOn);
	}

	public static void toggle(Category c) {
		ENABLED.put(c, !isEnabled(c));
		save();
	}

	public static boolean showToggleMessage() {
		return showToggleMessage;
	}

	public static void toggleShowToggleMessage() {
		showToggleMessage = !showToggleMessage;
		save();
	}

	public static void load() {
		for (Category c : Category.values()) ENABLED.put(c, c.defaultOn);
		if (!Files.exists(FILE)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, Boolean> data = GSON.fromJson(reader, new TypeToken<Map<String, Boolean>>() {}.getType());
			if (data == null) return;
			for (Category c : Category.values()) {
				Boolean value = data.get(c.name());
				if (value != null) ENABLED.put(c, value);
			}
			Boolean msg = data.get(KEY_TOGGLE_MESSAGE);
			if (msg != null) showToggleMessage = msg;
		} catch (Exception e) {
			System.err.println("[BlockESP] Could not read config, using defaults: " + e);
		}
	}

	public static void save() {
		Map<String, Boolean> data = new LinkedHashMap<>();
		data.put(KEY_TOGGLE_MESSAGE, showToggleMessage);
		for (Category c : Category.values()) data.put(c.name(), isEnabled(c));
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			System.err.println("[BlockESP] Could not save config: " + e);
		}
	}
}
