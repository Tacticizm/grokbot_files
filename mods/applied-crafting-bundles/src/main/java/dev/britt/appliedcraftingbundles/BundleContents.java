package dev.britt.appliedcraftingbundles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * What a Bundle Pattern holds: a player-chosen name and the items (with counts) that make up one "set".
 * Requesting N bundles later means N of every entry.
 */
public record BundleContents(String name, List<ItemStackTemplate> entries) {
    public static final BundleContents EMPTY = new BundleContents("", List.of());

    public static final Codec<BundleContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(BundleContents::name),
            ItemStackTemplate.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(BundleContents::entries)
    ).apply(i, BundleContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BundleContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BundleContents::name,
            ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), BundleContents::entries,
            BundleContents::new);

    public BundleContents {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
