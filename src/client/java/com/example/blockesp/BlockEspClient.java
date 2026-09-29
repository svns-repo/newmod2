package com.example.blockesp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class BlockEspClient implements ClientModInitializer {
	public static final String MOD_ID = "blockesp";

	// ---- Settings (tweak these) ----
	private static final int SCAN_INTERVAL_TICKS = 20; // rescan once per second
	private static final int MAX_TARGETS = 300;        // nearest N storage blocks get drawn
	private static final int ORE_SCAN_INTERVAL_TICKS = 40; // ore scans are heavier: every 2 seconds
	private static final int ORE_RADIUS_CHUNKS = 4;    // ore scan radius (4 chunks = 64 blocks)
	private static final int MAX_ORE_TARGETS = 300;    // nearest N ores get drawn
	private static final float ALPHA = 0.35f;          // box transparency

	// A copy of the vanilla debug filled-box pipeline with depth testing turned off,
	// which is what makes the boxes visible through walls.
	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/esp_filled_through_walls"))
					.withDepthStencilState(Optional.empty())
					.build()
	);

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final StagedVertexBuffer stagedBuffer =
			new StagedVertexBuffer(() -> "BlockESP Buffer", RenderType.SMALL_BUFFER_SIZE);

	private static KeyMapping toggleKey;
	private static KeyMapping menuKey;
	private static boolean enabled = false;
	private static int tickCounter = 0;
	private static int oreTickCounter = 0;

	// Filled on the client tick, snapshotted during extraction, read while drawing.
	private static volatile List<Target> scanned = List.of();
	private static List<Target> scannedBlocks = List.of();
	private static List<Target> scannedOres = List.of();
	private static volatile List<Target> renderTargets = List.of();

	/** One highlighted block. rgb is 0xRRGGBB. */
	private record Target(int x, int y, int z, int rgb) {
		double distSq(double px, double py, double pz) {
			double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockesp.toggle",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_X,
				category));

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockesp.menu",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_B,
				category));

		EspConfig.load();

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		LevelExtractionEvents.END_EXTRACTION.register(ctx -> renderTargets = enabled ? scanned : List.of());
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::renderAndDraw);
	}

	// ------------------------------------------------------------------
	// Scanning
	// ------------------------------------------------------------------

	private void onClientTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			setEnabled(!enabled);
			if (client.player != null && EspConfig.showToggleMessage()) {
				client.player.sendSystemMessage(Component.literal("Block ESP: " + (enabled ? "ON" : "OFF")));
			}
		}

		while (menuKey.consumeClick()) {
			if (client.player != null) {
				client.gui.setScreen(new EspConfigScreen());
			}
		}

		if (!enabled || client.level == null || client.player == null) {
			scanned = List.of();
			scannedBlocks = List.of();
			scannedOres = List.of();
			return;
		}

		boolean changed = false;
		int renderDistance = client.options.getEffectiveRenderDistance();

		if (++tickCounter >= SCAN_INTERVAL_TICKS) {
			tickCounter = 0;
			scannedBlocks = scanBlockEntities(client.level, client.player, renderDistance);
			changed = true;
		}

		if (++oreTickCounter >= ORE_SCAN_INTERVAL_TICKS) {
			oreTickCounter = 0;
			scannedOres = scanOres(client.level, client.player, Math.min(renderDistance, ORE_RADIUS_CHUNKS));
			changed = true;
		}

		if (changed) {
			List<Target> combined = new ArrayList<>(scannedBlocks.size() + scannedOres.size());
			combined.addAll(scannedBlocks);
			combined.addAll(scannedOres);
			scanned = List.copyOf(combined);
		}
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		requestRescan();
	}

	/** Makes the next client tick rescan immediately (used when settings change). */
	public static void requestRescan() {
		tickCounter = SCAN_INTERVAL_TICKS;
		oreTickCounter = ORE_SCAN_INTERVAL_TICKS;
	}

	private static List<Target> scanBlockEntities(ClientLevel level, Player player, int radiusChunks) {
		int pcx = SectionPos.blockToSectionCoord(player.getBlockX());
		int pcz = SectionPos.blockToSectionCoord(player.getBlockZ());
		List<Target> found = new ArrayList<>();

		for (int cx = pcx - radiusChunks; cx <= pcx + radiusChunks; cx++) {
			for (int cz = pcz - radiusChunks; cz <= pcz + radiusChunks; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) continue;

				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					int rgb = colorFor(entry.getValue());
					if (rgb < 0) continue;
					BlockPos pos = entry.getKey();
					found.add(new Target(pos.getX(), pos.getY(), pos.getZ(), rgb));
				}
			}
		}

		return nearest(found, player, MAX_TARGETS);
	}

	private static List<Target> scanOres(ClientLevel level, Player player, int radiusChunks) {
		List<EspConfig.Category> ores = new ArrayList<>();
		for (EspConfig.Category c : EspConfig.Category.values()) {
			if (c.isOre() && EspConfig.isEnabled(c)) ores.add(c);
		}
		if (ores.isEmpty()) return List.of();

		// Quick check used to skip whole 16x16x16 sections that contain none of the enabled ores.
		Predicate<BlockState> anyOre = state -> {
			for (EspConfig.Category c : ores) {
				if (c.oreMatcher.test(state)) return true;
			}
			return false;
		};

		int pcx = SectionPos.blockToSectionCoord(player.getBlockX());
		int pcz = SectionPos.blockToSectionCoord(player.getBlockZ());
		List<Target> found = new ArrayList<>();

		for (int cx = pcx - radiusChunks; cx <= pcx + radiusChunks; cx++) {
			for (int cz = pcz - radiusChunks; cz <= pcz + radiusChunks; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) continue;

				int baseX = SectionPos.sectionToBlockCoord(cx);
				int baseZ = SectionPos.sectionToBlockCoord(cz);
				LevelChunkSection[] sections = chunk.getSections();

				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					if (section == null || section.hasOnlyAir()) continue;
					if (!section.getStates().maybeHas(anyOre)) continue;

					int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));

					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState state = section.getBlockState(x, y, z);
								for (EspConfig.Category c : ores) {
									if (c.oreMatcher.test(state)) {
										found.add(new Target(baseX + x, baseY + y, baseZ + z, c.rgb));
										break;
									}
								}
							}
						}
					}
				}
			}
		}

		return nearest(found, player, MAX_ORE_TARGETS);
	}

	/** Sorts by distance to the player and keeps only the closest `limit`. */
	private static List<Target> nearest(List<Target> found, Player player, int limit) {
		double px = player.getX(), py = player.getY(), pz = player.getZ();
		found.sort(Comparator.comparingDouble(t -> t.distSq(px, py, pz)));
		if (found.size() > limit) {
			found = found.subList(0, limit);
		}
		return List.copyOf(found);
	}

	/** Returns 0xRRGGBB for enabled block types, or -1 to ignore. */
	private static int colorFor(BlockEntity be) {
		EspConfig.Category category = categoryOf(be);
		if (category == null || !EspConfig.isEnabled(category)) return -1;
		return category.rgb;
	}

	/** Maps a block entity to its menu category. Order matters: subclasses first. */
	private static EspConfig.Category categoryOf(BlockEntity be) {
		return switch (be) {
			case TrialSpawnerBlockEntity t      -> EspConfig.Category.TRIAL_SPAWNERS;
			case SpawnerBlockEntity s           -> EspConfig.Category.SPAWNERS;
			case ShulkerBoxBlockEntity s        -> EspConfig.Category.SHULKER_BOXES;
			case EnderChestBlockEntity e        -> EspConfig.Category.ENDER_CHESTS;
			case BarrelBlockEntity b            -> EspConfig.Category.BARRELS;
			case TrappedChestBlockEntity t      -> EspConfig.Category.TRAPPED_CHESTS;
			case ChestBlockEntity c             -> EspConfig.Category.CHESTS;
			case HopperBlockEntity h            -> EspConfig.Category.HOPPERS;
			case DispenserBlockEntity d         -> EspConfig.Category.DISPENSERS; // droppers too
			case AbstractFurnaceBlockEntity f   -> EspConfig.Category.FURNACES;   // + smokers, blast furnaces
			default -> null;
		};
	}

	// ------------------------------------------------------------------
	// Drawing (based on the Fabric docs "Rendering in the World" example)
	// ------------------------------------------------------------------

	private void renderAndDraw(LevelRenderContext context) {
		List<Target> targets = renderTargets;
		if (targets.isEmpty()) return;

		RenderPipeline pipeline = FILLED_THROUGH_WALLS;
		VertexFormat format = pipeline.getVertexFormatBinding(0);
		if (format == null) return;

		PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
		StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(format, primitive,
				primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

		VertexConsumer builder = stagedBuffer.getVertexBuilder(draw);
		Matrix4fc pose = context.poseStack().last().pose();
		Vec3 cam = context.levelState().cameraRenderState.pos;

		for (Target t : targets) {
			// Camera-relative coordinates keep float precision good far from 0,0.
			float x = (float) (t.x() - cam.x);
			float y = (float) (t.y() - cam.y);
			float z = (float) (t.z() - cam.z);
			float r = ((t.rgb() >> 16) & 0xFF) / 255f;
			float g = ((t.rgb() >> 8) & 0xFF) / 255f;
			float b = (t.rgb() & 0xFF) / 255f;
			filledBox(pose, builder, x, y, z, x + 1, y + 1, z + 1, r, g, b, ALPHA);
		}

		stagedBuffer.upload();
		StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
		if (info != null) {
			submit(Minecraft.getInstance(), info, pipeline);
		}
		stagedBuffer.endFrame();
	}

	private static void filledBox(Matrix4fc m, VertexConsumer buf,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float r, float g, float b, float a) {
		// front
		buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		// back
		buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		// left
		buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		// right
		buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		// top
		buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		// bottom
		buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
	}

	private static void submit(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();
		if (colorTexture == null) return;

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> MOD_ID + " esp pass", colorTexture, Optional.empty(),
						mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, info.vertexBuffer().slice());
			pass.setIndexBuffer(info.indexBuffer(), info.indexType());
			pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	/** Called from GameRendererMixin when the game renderer shuts down. */
	public static void close() {
		stagedBuffer.close();
	}
}
