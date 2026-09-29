package com.example.blockesp;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The popup menu for turning ESP, ores and individual block types on/off. */
public class EspConfigScreen extends Screen {
	private static final int COLUMNS = 3;
	private static final int BUTTON_W = 125;
	private static final int BUTTON_H = 20;
	private static final int GAP = 2;
	private static final int ROW = BUTTON_H + GAP;

	private int storageHeaderY;
	private int oreHeaderY;

	public EspConfigScreen() {
		super(Component.literal("Block ESP Settings"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int gridW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
		int left = centerX - gridW / 2;
		int half = (gridW - GAP) / 2;
		int y = 20;

		// Top row: master switch + chat message switch
		this.addRenderableWidget(Button.builder(masterLabel(), btn -> {
			BlockEspClient.setEnabled(!BlockEspClient.isEnabled());
			btn.setMessage(masterLabel());
		}).bounds(left, y, half, BUTTON_H).build());

		this.addRenderableWidget(Button.builder(messageLabel(), btn -> {
			EspConfig.toggleShowToggleMessage();
			btn.setMessage(messageLabel());
		}).bounds(left + half + GAP, y, half, BUTTON_H).build());

		List<EspConfig.Category> storage = new ArrayList<>();
		List<EspConfig.Category> ores = new ArrayList<>();
		for (EspConfig.Category c : EspConfig.Category.values()) {
			(c.isOre() ? ores : storage).add(c);
		}

		y += BUTTON_H + 8;
		storageHeaderY = y;
		y = addGrid(storage, left, y + 12);

		y += 6;
		oreHeaderY = y;
		y = addGrid(ores, left, y + 12);

		this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
				.bounds(centerX - 100, y + 6, 200, BUTTON_H).build());
	}

	/** Adds a grid of toggle buttons and returns the Y just below it. */
	private int addGrid(List<EspConfig.Category> categories, int left, int top) {
		for (int i = 0; i < categories.size(); i++) {
			EspConfig.Category category = categories.get(i);
			int x = left + (i % COLUMNS) * (BUTTON_W + GAP);
			int y = top + (i / COLUMNS) * ROW;

			this.addRenderableWidget(Button.builder(categoryLabel(category), btn -> {
				EspConfig.toggle(category);
				BlockEspClient.requestRescan();
				btn.setMessage(categoryLabel(category));
			}).bounds(x, y, BUTTON_W, BUTTON_H).build());
		}
		int rows = (categories.size() + COLUMNS - 1) / COLUMNS;
		return top + rows * ROW;
	}

	private static MutableComponent onOff(boolean on) {
		return Component.literal(on ? "ON" : "OFF").withStyle(s -> s.withColor(on ? 0x55FF55 : 0xFF5555));
	}

	private static Component masterLabel() {
		return Component.literal("ESP: ").append(onOff(BlockEspClient.isEnabled()));
	}

	private static Component messageLabel() {
		return Component.literal("Chat Message: ").append(onOff(EspConfig.showToggleMessage()));
	}

	private static Component categoryLabel(EspConfig.Category category) {
		MutableComponent square = Component.literal("■ ").withStyle(s -> s.withColor(category.rgb));
		return square.append(Component.literal(category.label + ": ")).append(onOff(EspConfig.isEnabled(category)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		centered(graphics, this.title.getString(), 6, 0xFFFFFFFF);
		centered(graphics, "Storage & Spawners", storageHeaderY, 0xFFFFFF80);
		centered(graphics, "Ores", oreHeaderY, 0xFFFFFF80);
	}

	private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
		graphics.text(this.font, text, this.width / 2 - this.font.width(text) / 2, y, color, true);
	}

	// Keep the game running behind the menu so you can see changes live.
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}
}
