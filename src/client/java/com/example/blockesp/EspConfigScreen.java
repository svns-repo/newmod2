package com.example.blockesp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The popup menu for turning ESP and individual block types on/off. */
public class EspConfigScreen extends Screen {
	private static final int BUTTON_W = 150;
	private static final int BUTTON_H = 20;
	private static final int GAP = 4;

	public EspConfigScreen() {
		super(Component.literal("Block ESP Settings"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = 40;

		// Master on/off switch
		this.addRenderableWidget(Button.builder(masterLabel(), btn -> {
			BlockEspClient.setEnabled(!BlockEspClient.isEnabled());
			btn.setMessage(masterLabel());
		}).bounds(centerX - BUTTON_W - GAP / 2, y, BUTTON_W * 2 + GAP, BUTTON_H).build());

		y += BUTTON_H + GAP * 3;

		// One toggle per block type, in two columns
		EspConfig.Category[] categories = EspConfig.Category.values();
		for (int i = 0; i < categories.length; i++) {
			EspConfig.Category category = categories[i];
			int column = i % 2;
			int row = i / 2;
			int x = column == 0 ? centerX - BUTTON_W - GAP / 2 : centerX + GAP / 2;
			int by = y + row * (BUTTON_H + GAP);

			this.addRenderableWidget(Button.builder(categoryLabel(category), btn -> {
				EspConfig.toggle(category);
				BlockEspClient.requestRescan();
				btn.setMessage(categoryLabel(category));
			}).bounds(x, by, BUTTON_W, BUTTON_H).build());
		}

		int rows = (categories.length + 1) / 2;
		int doneY = y + rows * (BUTTON_H + GAP) + GAP * 2;

		this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
				.bounds(centerX - 100, doneY, 200, BUTTON_H).build());
	}

	private static Component masterLabel() {
		boolean on = BlockEspClient.isEnabled();
		return Component.literal("ESP: ")
				.append(Component.literal(on ? "ON" : "OFF")
						.withStyle(s -> s.withColor(on ? 0x55FF55 : 0xFF5555)));
	}

	private static Component categoryLabel(EspConfig.Category category) {
		boolean on = EspConfig.isEnabled(category);
		MutableComponent square = Component.literal("■ ").withStyle(s -> s.withColor(category.rgb));
		MutableComponent state = Component.literal(on ? "ON" : "OFF")
				.withStyle(s -> s.withColor(on ? 0x55FF55 : 0xFF5555));
		return square.append(Component.literal(category.label + ": ")).append(state);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String title = this.title.getString();
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 18, 0xFFFFFFFF, true);
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
