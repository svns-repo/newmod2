package com.example.blockesp;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/** Which block types are highlighted. Saved to .minecraft/config/blockesp.json */
public final class EspConfig {
	public enum Category {
		CHESTS("Chests", 0xFFD700, true),
		TRAPPED_CHESTS("Trapped Chests", 0xFF5050, true),
		BARRELS("Barrels", 0xA0522D, true),
		SHULKER_BOXES("Shulker Boxes", 0xB050FF, true),
		ENDER_CHESTS("Ender Chests", 0x20C0A0, true),
		SPAWNERS("Spawners", 0xFF2020, true),
		TRIAL_SPAWNERS("Trial Spawners", 0xFF8C00, true),
		HOPPERS("Hoppers", 0x808080, false),
		DISPENSERS("Dispensers/Droppers", 0x6080FF, false),
		FURNACES("Furnaces", 0xE0E0E0, false);

		public final String label;
		public final int rgb;
		public final boolean defaultOn;

		Category(String label, int rgb, boolean defaultOn) {
			this.label = label;
			this.rgb = rgb;
			this.defaultOn = defaultOn;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blockesp.json");
	private static final EnumMap<Category, Boolean> ENABLED = new EnumMap<>(Category.class);

	private EspConfig() {}

	public static boolean isEnabled(Category c) {
		return ENABLED.getOrDefault(c, c.defaultOn);
	}

	public static void toggle(Category c) {
		ENABLED.put(c, !isEnabled(c));
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
		} catch (Exception e) {
			System.err.println("[BlockESP] Could not read config, using defaults: " + e);
		}
	}

	public static void save() {
		Map<String, Boolean> data = new LinkedHashMap<>();
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
