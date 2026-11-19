package com.ruadou.personaldimension.dimension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DebugStickItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class GuardService {

    private GuardService() {}

    private static final Map<UUID, Long> LAST_NOTIFY = new ConcurrentHashMap<>();
    private static final long NOTIFY_INTERVAL_MS = 1000L;

    private static final Set<UUID> PENDING_REDIRECT = ConcurrentHashMap.newKeySet();

    public static void markPendingRedirect(UUID playerId) {
        PENDING_REDIRECT.add(playerId);
    }

    public static boolean takePendingRedirect(UUID playerId) {
        return PENDING_REDIRECT.remove(playerId);
    }

    public static void removePendingRedirect(UUID playerId) {
        PENDING_REDIRECT.remove(playerId);
    }

    public static boolean isVisitor(Player player) {
        if (!(player instanceof ServerPlayer sp)) return false;
        return Platform.ownerOf(sp.level().dimension()).filter(id -> !id.equals(sp.getUUID())).isPresent();
    }

    private static final TagKey<Item> CAPTURE_ITEMS = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("personaldimension", "capture_items"));
    private static final TagKey<Item> RELEASE_ITEMS = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("personaldimension", "release_items"));
    private static final TagKey<Block> WORKSTATIONS = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("personaldimension", "workstations"));

    public static boolean denyBed(Player player, Level level, BlockPos pos) {
        if (isVisitor(player) && level.getBlockState(pos).getBlock() instanceof BedBlock) {
            if (player instanceof ServerPlayer sp) sp.displayClientMessage(
                Component.translatable("personaldimension.message.visitor_bed_denied"), true);
            return true;
        }
        return false;
    }

    public static Permission breakPerm(BlockState state) {
        return isCrop(state) ? Permission.HARVEST_CROP : Permission.BREAK;
    }

    public static Permission placePerm(BlockState state) {
        return isCrop(state) ? Permission.PLANT_CROP : Permission.PLACE;
    }

    private static boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS)
            || state.is(Blocks.NETHER_WART) || state.is(Blocks.COCOA);
    }

    private static boolean isWorkstation(BlockState state) {
        return state.is(WORKSTATIONS) || state.is(Blocks.CRAFTING_TABLE)
            || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE)
            || state.is(Blocks.SMOKER) || state.is(Blocks.BREWING_STAND)
            || state.is(Blocks.STONECUTTER) || state.is(Blocks.SMITHING_TABLE)
            || state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL)
            || state.is(Blocks.DAMAGED_ANVIL) || state.is(Blocks.GRINDSTONE)
            || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.LOOM)
            || state.is(Blocks.ENCHANTING_TABLE);
    }

    public static Permission entityInteractPerm(Player player, Entity entity, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(CAPTURE_ITEMS)) return Permission.CAPTURE_MOB;
        if (entity instanceof Animal animal && animal.isFood(stack)) return Permission.FEED_ANIMAL;
        if (entity instanceof Container && player.isShiftKeyDown()) return Permission.CONTAINER;
        if (!player.isShiftKeyDown() && (entity instanceof Boat || entity instanceof AbstractMinecart
            || entity instanceof AbstractHorse)) return Permission.RIDE_VEHICLE;
        return Permission.INTERACT_ENTITY;
    }

    public static boolean deny(Player player, Permission perm) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        ResourceKey<Level> dim = sp.level().dimension();
        if (Platform.isPublicDimension(dim)) {
            return false;
        }
        Optional<UUID> owner = Platform.ownerOf(dim);
        if (owner.isEmpty()) {
            return false;
        }
        if (owner.get().equals(sp.getUUID())) {
            return false;
        }
        MinecraftServer server = sp.getServer();
        if (server == null) {
            return false;
        }
        if (FriendService.hasPermission(server, owner.get(), sp.getUUID(), perm)) {
            return false;
        }
        notifyDenied(sp, perm);
        return true;
    }

    public static void resyncInventory(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.containerMenu.sendAllDataToRemote();
        }
    }

    public static boolean isContainer(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container || be instanceof MenuProvider;
    }

    public static Permission blockInteractPerm(ItemStack stack, Level level, BlockPos pos) {
        Item item = stack.getItem();
        BlockState state = level.getBlockState(pos);
        if (stack.is(RELEASE_ITEMS)) return Permission.RELEASE_MOB;
        if (item instanceof BlockItem bi && isCrop(bi.getBlock().defaultBlockState())) return Permission.PLANT_CROP;
        if (isModifyBlockItem(item)) return Permission.MODIFY_BLOCK;
        if (isCrop(state)) return Permission.HARVEST_CROP;
        if (isWorkstation(state)) return Permission.WORKSTATION;
        if (isContainer(level, pos)) {
            return Permission.CONTAINER;
        }
        if (item instanceof BucketItem) {
            return Permission.USE_BUCKET;
        }
        if (isModifyBlockItem(item)) {
            return Permission.MODIFY_BLOCK;
        }
        if (isPlaceEntityItem(item)) {
            return Permission.SPAWN_ENTITY;
        }
        return Permission.INTERACT_BLOCK;
    }

    public static Permission useItemPerm(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(RELEASE_ITEMS)) return Permission.RELEASE_MOB;
        if (item instanceof BucketItem) {
            return Permission.USE_BUCKET;
        }
        if (isPlaceEntityItem(item)) {
            return Permission.SPAWN_ENTITY;
        }
        if (isRangedItem(item)) {
            return Permission.USE_PROJECTILE;
        }
        return null;
    }

    private static boolean isRangedItem(Item item) {
        if (item instanceof FireChargeItem) {
            return false;
        }
        return item instanceof ProjectileWeaponItem
            || item instanceof ProjectileItem
            || item instanceof EnderpearlItem
            || item instanceof FishingRodItem;
    }

    private static boolean isModifyBlockItem(Item item) {
        return item instanceof FlintAndSteelItem
            || item instanceof FireChargeItem
            || item instanceof HoeItem
            || item instanceof ShovelItem
            || item instanceof AxeItem
            || item instanceof BoneMealItem
            || item instanceof HoneycombItem
            || item instanceof ShearsItem
            || item instanceof BrushItem
            || item instanceof DebugStickItem;
    }

    private static boolean isPlaceEntityItem(Item item) {
        return item instanceof SpawnEggItem
            || item instanceof BoatItem
            || item instanceof MinecartItem
            || item instanceof ArmorStandItem
            || item instanceof EndCrystalItem
            || item instanceof HangingEntityItem
            || item instanceof LeadItem
            || item instanceof FireworkRocketItem;
    }

    /**
     * Server-side storage restrictions. Known vanilla storage menus support direction-specific
     * transfers. Unknown/shared processing menus require both directions before modification.
     */
    public static boolean denyContainerClick(Player player, AbstractContainerMenu menu,
                                               int slotNum, int button, ClickType type) {
        if (!isVisitor(player) || menu == player.inventoryMenu) return false;
        // These vanilla menus use temporary player-owned input/output rather than shared storage.
        if (menu instanceof CraftingMenu || menu instanceof StonecutterMenu
            || menu instanceof SmithingMenu || menu instanceof AnvilMenu
            || menu instanceof GrindstoneMenu || menu instanceof CartographyTableMenu
            || menu instanceof LoomMenu || menu instanceof EnchantmentMenu) {
            return deny(player, Permission.WORKSTATION);
        }
        boolean simple = menu.getClass() == ChestMenu.class || menu.getClass() == HopperMenu.class
            || menu.getClass() == ShulkerBoxMenu.class || menu.getClass() == DispenserMenu.class;
        if (type == ClickType.PICKUP_ALL) return deny(player, Permission.TAKE_CONTAINER);
        if (slotNum < 0 || slotNum >= menu.slots.size()) return false;
        Slot slot = menu.getSlot(slotNum);
        boolean own = slot.container == player.getInventory();
        if (!simple) {
            if (own && (type == ClickType.PICKUP || type == ClickType.THROW || type == ClickType.SWAP
                || type == ClickType.CLONE)) return false;
            return deny(player, Permission.TAKE_CONTAINER) || deny(player, Permission.PUT_CONTAINER);
        }
        if (type == ClickType.QUICK_MOVE) return deny(player, own ? Permission.PUT_CONTAINER : Permission.TAKE_CONTAINER);
        if (own) return false;
        return switch (type) {
            case THROW, CLONE -> deny(player, Permission.TAKE_CONTAINER);
            case QUICK_CRAFT -> deny(player, Permission.PUT_CONTAINER);
            case SWAP -> deny(player, Permission.TAKE_CONTAINER) || deny(player, Permission.PUT_CONTAINER);
            case PICKUP -> {
                ItemStack carried = menu.getCarried();
                ItemStack stored = slot.getItem();
                if (carried.isEmpty()) yield deny(player, Permission.TAKE_CONTAINER);
                if (stored.isEmpty()) yield deny(player, Permission.PUT_CONTAINER);
                if (slot.mayPlace(carried) && ItemStack.isSameItemSameComponents(carried, stored)
                    && stored.getCount() < slot.getMaxStackSize(carried)) yield deny(player, Permission.PUT_CONTAINER);
                yield deny(player, Permission.TAKE_CONTAINER) || deny(player, Permission.PUT_CONTAINER);
            }
            default -> deny(player, Permission.TAKE_CONTAINER) || deny(player, Permission.PUT_CONTAINER);
        };
    }

    public static Permission attackPerm(Entity target) {
        return target instanceof Player ? Permission.PVP : Permission.ATTACK;
    }

    private static void notifyDenied(ServerPlayer sp, Permission perm) {
        long now = System.currentTimeMillis();
        Long last = LAST_NOTIFY.get(sp.getUUID());
        if (last != null && now - last < NOTIFY_INTERVAL_MS) {
            return;
        }
        LAST_NOTIFY.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.translatable(
            "personaldimension.message.no_permission",
            Component.translatable(perm.translationKey())), true);
    }

    public static void onPlayerLoggedOut(ServerPlayer sp) {
        LAST_NOTIFY.remove(sp.getUUID());
        PENDING_REDIRECT.remove(sp.getUUID());
    }
}
