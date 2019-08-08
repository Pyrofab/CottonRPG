package io.github.cottonmc.cottonrpg.util;

import io.github.cottonmc.cottonrpg.data.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.packet.CustomPayloadS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;


public class CottonRPGNetworking {
	public static final Identifier BATCH_CLASSES = new Identifier("cotton-rpg", "batch_classes");
	public static final Identifier BATCH_RESOURCES = new Identifier("cotton-rpg", "batch_resources");
	public static final Identifier SINGLE_CLASS = new Identifier("cotton-rpg", "single_class");
	public static final Identifier SINGLE_RESOURCE = new Identifier("cotton-rpg", "single_resource");

	public static void init() {
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) initClient();
		initServer();
	}

	public static void initClient() {
		ClientSidePacketRegistry.INSTANCE.register(BATCH_CLASSES, (ctx, buf) -> {
			//Unpack data on the netty thread, while the buffer is still available.
			boolean clear = buf.readBoolean();
			int count = buf.readInt();
			List<CharacterClassEntry> read = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				CharacterClassEntry entry = new CharacterClassEntry(buf.readIdentifier());
				entry.setLevel(buf.readInt());
				entry.setExperience(buf.readInt());
				read.add(entry);
			}

			ctx.getTaskQueue().execute( ()->{ //DO NOT access the buffer past this point! DO NOT access the world or player before this point!

				//Now that worlds and players are in-scope, get the CharacterData and pour our unpacked entries into it.
				PlayerEntity player = ctx.getPlayer();
				CharacterData data = CharacterData.get(player);
				if (data==null) {
					System.out.println("Actor not ready yet!");
					return;
				}
				CharacterClasses classes = data.getClasses();
				if (clear) {
					classes.clear();
					for (CharacterClassEntry entry : read) {
						classes.giveIfAbsent(entry);
					}
				} else {
					for (CharacterClassEntry entry : read) {
						classes.giveIfAbsent(entry);
						CharacterClassEntry set = classes.get(entry.id);
						set.setLevel(entry.getLevel());
						set.setExperience(entry.getExperience());
					}
				}
			});
		});
		ClientSidePacketRegistry.INSTANCE.register(BATCH_RESOURCES, (ctx, buf) -> {
			//Unpack data on the netty thread, while the buffer is still available.
			boolean clear = buf.readBoolean();
			int count = buf.readInt();
			List<CharacterResourceEntry> read = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				CharacterResourceEntry entry = new CharacterResourceEntry(buf.readIdentifier());
				entry.setCurrent(buf.readLong());
				entry.setMax(buf.readLong());
				read.add(entry);
			}

			ctx.getTaskQueue().execute( ()->{ //DO NOT access the buffer past this point! DO NOT access the world or player before this point!

				//Now that worlds and players are in-scope, get the CharacterData and pour our unpacked entries into it.
				PlayerEntity player = ctx.getPlayer();
				CharacterData data = CharacterData.get(player);
				if (data==null) {
					System.out.println("Actor not ready yet!");
					return;
				}
				CharacterResources resources = data.getResources();
				if (clear) {
					resources.clear();
					for (CharacterResourceEntry entry : read) {
						resources.giveIfAbsent(entry);
					}
				} else {
					for (CharacterResourceEntry entry : read) {
						resources.giveIfAbsent(entry);
						CharacterResourceEntry set = resources.get(entry.id);
						set.setCurrent(entry.getCurrent());
						set.setMax(entry.getMax());
					}
				}
			});
		});
		ClientSidePacketRegistry.INSTANCE.register(SINGLE_CLASS, ((ctx, buf) -> {
			Identifier id = buf.readIdentifier();
			CharacterClasses classes = CharacterData.get(ctx.getPlayer()).getClasses();
			classes.giveIfAbsent(new CharacterClassEntry(id));
			CharacterClassEntry entry = classes.get(id);
			entry.setLevel(buf.readInt());
			entry.setExperience(buf.readInt());
		}));
		ClientSidePacketRegistry.INSTANCE.register(SINGLE_RESOURCE, ((ctx, buf) -> {
			Identifier id = buf.readIdentifier();
			CharacterResources resources = CharacterData.get(ctx.getPlayer()).getResources();
			resources.giveIfAbsent(new CharacterResourceEntry(id));
			CharacterResourceEntry entry = resources.get(id);
			entry.setCurrent(buf.readLong());
			entry.setMax(buf.readLong());
		}));
	}

	public static void initServer() {
	}

	public static boolean batchSyncClasses(ServerPlayerEntity player, CharacterClasses data, boolean syncAll) {
		if (player.networkHandler==null) return false;

		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(syncAll);
		if (syncAll) {
			buf.writeInt(data.getSize());
			data.forEach((id, entry) -> {
				buf.writeIdentifier(id);
				buf.writeInt(entry.getLevel());
				buf.writeInt(entry.getExperience());
				entry.clearDirty();
			});
		} else {
			List<CharacterClassEntry> entries = new ArrayList<>();
			data.forEach((id, entry) -> {
				if (entry.isDirty()) entries.add(entry);
			});
			buf.writeInt(entries.size());
			for (CharacterClassEntry entry : entries) {
				buf.writeIdentifier(entry.id);
				buf.writeInt(entry.getLevel());
				buf.writeInt(entry.getExperience());
			}
		}

		ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, new CustomPayloadS2CPacket(BATCH_CLASSES, buf));

		return true;
	}

	public static boolean batchSyncResources(ServerPlayerEntity player, CharacterResources data, boolean syncAll) {
		if (player.networkHandler==null) return false;

		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(syncAll);
		if (syncAll) {
			buf.writeInt(data.getSize());
			data.forEach((id, entry) -> {
				buf.writeIdentifier(id);
				buf.writeLong(entry.getCurrent());
				buf.writeLong(entry.getMax());
				entry.clearDirty();
			});
		} else {
			List<CharacterResourceEntry> entries = new ArrayList<>();
			data.forEach((id, entry) -> {
				if (entry.isDirty()) entries.add(entry);
			});
			buf.writeInt(entries.size());
			for (CharacterResourceEntry entry : entries) {
				buf.writeIdentifier(entry.id);
				buf.writeLong(entry.getCurrent());
				buf.writeLong(entry.getMax());
			}
		}
		
		ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, new CustomPayloadS2CPacket(BATCH_RESOURCES, buf));
		
		return true;
	}

	public static boolean syncClassChange(ServerPlayerEntity player, CharacterClassEntry entry) {
		if (player.networkHandler == null) return false;

		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeIdentifier(entry.id);
		buf.writeInt(entry.getLevel());
		buf.writeInt(entry.getExperience());
		ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, new CustomPayloadS2CPacket(SINGLE_CLASS, buf));
		return true;
	}

	public static boolean syncResourceChange(ServerPlayerEntity player, CharacterResourceEntry entry) {
		if (player.networkHandler==null) return false;
		
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeIdentifier(entry.id);
		buf.writeLong(entry.getCurrent());
		buf.writeLong(entry.getMax());
		entry.clearDirty();
		ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, new CustomPayloadS2CPacket(SINGLE_RESOURCE, buf));
		return true;
	}
}
