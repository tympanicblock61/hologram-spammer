package com.hologram.spammer.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HoloImage extends Command {
    static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(2)).build();

    public HoloImage() {
        super("HoloImage", "Creates armor stands that build a certain image");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
            argument("url", StringArgumentType.greedyString())
                .executes(
                    ctx -> run(
                        StringArgumentType.getString(ctx, "url"),
                        100
                    )
                ).then(
                    argument("size", IntegerArgumentType.integer(10, 1000))
                        .executes(
                            ctx -> run(
                                StringArgumentType.getString(ctx, "url"),
                                IntegerArgumentType.getInteger(ctx, "size")
                            )
                        )
                )
        );
    }

    static int run(String url, Integer size) throws CommandException {
        ChatUtils.info("Downloading image...");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "meteor/1.0").build();
        try {
            HttpResponse<byte[]> send = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = send.body();
            BufferedImage read = ImageIO.read(new ByteArrayInputStream(body));
            ChatUtils.info("Downloaded image, resizing");
            int newWidth = size;
            double ratio = (double) newWidth / read.getWidth();
            int newHeight = (int) (read.getHeight() * ratio);
            BufferedImage n = resize(read, newWidth, newHeight);

            List<MutableText> names = new ArrayList<>();
            float yOffset = n.getHeight() / 4.5f;
            for (int y = 0; y < n.getHeight(); y++) {
                MutableText currentName = Text.literal("");
                for (int x = 0; x < n.getWidth(); x++) {
                    int rgb = n.getRGB(x, y);
                    Color c = new Color(rgb);
                    int t = c.getRed() << 16 | c.getGreen() << 8 | c.getBlue();
                    currentName.append(Text.literal("█").styled(style -> style.withColor(t)));
                }
                names.add(currentName);
            }
            List<List<MutableText>> partition = Lists.partition(names, 9 * 3);
            List<NbtCompound> shulkers = new ArrayList<>();
            int total = 0;
            Vec3d pos = mc.player.getPos();
            for (List<MutableText> mutableTexts : partition) {
                List<NbtCompound> stacks = new ArrayList<>();
                for (MutableText mutableText : mutableTexts) {
                    NbtCompound entityTag = new NbtCompound();
                    entityTag.putString("id", "minecraft:armor_stand");
                    entityTag.putBoolean("NoGravity", true);
                    entityTag.putBoolean("Invisible", true);
                    entityTag.putBoolean("Marker", true);
                    NbtList posList = new NbtList();
                    posList.add(NbtDouble.of(pos.x));
                    posList.add(NbtDouble.of(pos.y + yOffset));
                    posList.add(NbtDouble.of(pos.z));
                    entityTag.put("Pos", posList);
                    entityTag.putBoolean("CustomNameVisible", true);
                    entityTag.putString("CustomName", Text.Serializer.toJson(mutableText));
                    NbtCompound x = new NbtCompound();
                    x.put("EntityTag", entityTag);
                    stacks.add(x);
                    total++;
                    yOffset -= 1 / 4.5f;
                }
                NbtList itemTags = new NbtList();
                for (int i = 0; i < stacks.size(); i++) {
                    NbtCompound itemTag = new NbtCompound();
                    itemTag.putByte("Slot", (byte) i);
                    itemTag.putString("id", Registries.ITEM.getId(Items.BAT_SPAWN_EGG).toString());
                    itemTag.putByte("Count", (byte) 1);
                    itemTag.put("tag", stacks.get(i));
                    itemTags.add(itemTag);
                }
                NbtCompound v = new NbtCompound();
                v.put("Items", itemTags);
                NbtCompound b = new NbtCompound();
                b.put("BlockEntityTag", v);
                shulkers.add(b);

            }
            ItemStack chest = new ItemStack(Items.CHEST);
            NbtList itemTags = new NbtList();
            for (int i = 0; i < shulkers.size(); i++) {
                NbtCompound itemTag = new NbtCompound();
                itemTag.putByte("Slot", (byte) i);
                itemTag.putString("id", Registries.ITEM.getId(Items.SHULKER_BOX).toString());
                itemTag.putByte("Count", (byte) 1);
                itemTag.put("tag", shulkers.get(i));
                itemTags.add(itemTag);
            }
            NbtCompound blockEntityTag = new NbtCompound();
            blockEntityTag.put("Items", itemTags);
            NbtCompound chestTag = new NbtCompound();
            chestTag.put("BlockEntityTag", blockEntityTag);
            chest.setNbt(chestTag);
            mc.interactionManager.clickCreativeStack(chest, slotIndexToId(mc.player.getInventory().selectedSlot));
            ChatUtils.info("Gave you a chest with all the spawn eggs");
            ChatUtils.info(String.format("Total spawn eggs: %s. Total shulkers: %s", total, shulkers.size()));
        } catch (IOException | InterruptedException e) {
            ChatUtils.error("Failed to download the image: " + e.getMessage());
            e.printStackTrace();
        }
        return SINGLE_SUCCESS;
    }

    private static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }

    public static int slotIndexToId(int index) {
        int translatedSlotId;
        if (index >= 0 && index < 9) {
            translatedSlotId = 36 + index;
        } else {
            translatedSlotId = index;
        }
        return translatedSlotId;
    }
}


