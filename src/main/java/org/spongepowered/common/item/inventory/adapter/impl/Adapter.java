/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.item.inventory.adapter.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Streams;
import net.minecraft.inventory.IInventory;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Container;
import org.spongepowered.api.item.inventory.EmptyInventory;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryProperty;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult.Type;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.common.item.inventory.EmptyInventoryImpl;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.custom.CustomInventory;
import org.spongepowered.common.item.inventory.lens.Fabric;
import org.spongepowered.common.item.inventory.lens.Lens;
import org.spongepowered.common.item.inventory.lens.LensProvider;
import org.spongepowered.common.item.inventory.lens.SlotProvider;
import org.spongepowered.common.item.inventory.lens.impl.DefaultEmptyLens;
import org.spongepowered.common.item.inventory.lens.impl.DefaultIndexedLens;
import org.spongepowered.common.item.inventory.lens.impl.collections.SlotCollection;
import org.spongepowered.common.item.inventory.lens.slots.SlotLens;
import org.spongepowered.common.item.inventory.observer.InventoryEventArgs;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.text.translation.SpongeTranslation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class Adapter implements MinecraftInventoryAdapter {

    public static abstract class Logic {

        private Logic() {}

        public static Optional<ItemStack> pollSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
            return Logic.pollSequential(adapter.getInventory(), adapter.getRootLens());
        }

        public static Optional<ItemStack> pollSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
            return Logic.findStack(inv, lens, true);
        }

        public static Optional<ItemStack> pollSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, int limit) {
            return Logic.pollSequential(adapter.getInventory(), adapter.getRootLens(), limit);
        }

        public static Optional<ItemStack> pollSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, int limit) {
            return Logic.findStacks(inv, lens, limit, true);
        }

        public static Optional<ItemStack> peekSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
            return Logic.peekSequential(adapter.getInventory(), adapter.getRootLens());
        }

        public static Optional<ItemStack> peekSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
            return Logic.findStack(inv, lens, false);
        }

        public static Optional<ItemStack> peekSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, int limit) {
            return Logic.peekSequential(adapter.getInventory(), adapter.getRootLens(), limit);
        }

        public static Optional<ItemStack> peekSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, int limit) {
            return Logic.findStacks(inv, lens, limit, false);
        }

        private static Optional<ItemStack> findStack(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, boolean remove) {
            if (lens == null) {
                return Optional.empty();
            }
            for (int ord = 0; ord < lens.slotCount(); ord++) {
                net.minecraft.item.ItemStack stack = lens.getStack(inv, ord);
                if (stack.isEmpty() || (remove && !lens.setStack(inv, ord, net.minecraft.item.ItemStack.EMPTY))) {
                    continue;
                }
                return ItemStackUtil.cloneDefensiveOptional(stack);
            }

            return Optional.empty();
        }

        private static Optional<ItemStack> findStacks(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, int limit, boolean remove) {

            if (lens == null) {
                return Optional.empty();
            }

            ItemStack result = null;

            for (int ord = 0; ord < lens.slotCount(); ord++) {
                net.minecraft.item.ItemStack stack = lens.getStack(inv, ord);
                if (stack.isEmpty() || stack.getCount() < 1 || (result != null && !result.getType().equals(stack.getItem()))) {
                    continue;
                }

                if (result == null) {
                    result = ItemStackUtil.cloneDefensive(stack, 0);
                }

                int pull = Math.min(stack.getCount(), limit);
                result.setQuantity(result.getQuantity() + pull);
                limit -= pull;

                if (!remove) {
                    continue;
                }

                if (pull >= stack.getCount()) {
                    lens.setStack(inv, ord, net.minecraft.item.ItemStack.EMPTY);
                } else {
                    stack.setCount(stack.getCount() - pull);
                }
            }

            return Optional.ofNullable(result);
        }

        public static InventoryTransactionResult insertSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack) {
            return Logic.insertSequential(adapter.getInventory(), adapter.getRootLens(), stack);
        }

        public static InventoryTransactionResult insertSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack) {
            if (lens == null) {
                return InventoryTransactionResult.builder().type(Type.FAILURE).reject(ItemStackUtil.cloneDefensive(stack)).build();
            }
            try {
                return Logic.insertStack(inv, lens, stack);
            } catch (Exception ex) {
               return InventoryTransactionResult.builder().type(Type.ERROR).reject(ItemStackUtil.cloneDefensive(stack)).build();
            }
        }

        private static InventoryTransactionResult insertStack(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack) {
            InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(Type.SUCCESS);
            net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

            int maxStackSize = Math.min(lens.getMaxStackSize(inv), nativeStack.getMaxStackSize());
            int remaining = stack.getQuantity();

            for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
                net.minecraft.item.ItemStack old = lens.getStack(inv, ord);
                int push = Math.min(remaining, maxStackSize);
                if (lens.setStack(inv, ord, ItemStackUtil.cloneDefensiveNative(nativeStack, push))) {
                    result.replace(ItemStackUtil.fromNative(old));
                    remaining -= push;
                }
            }

            if (remaining > 0) {
                result.reject(ItemStackUtil.cloneDefensive(nativeStack, remaining));
            }

            return result.build();
        }

        public static InventoryTransactionResult appendSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack) {
            return Logic.appendSequential(adapter.getInventory(), adapter.getRootLens(), stack);
        }

        public static InventoryTransactionResult appendSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack) {
            InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(Type.SUCCESS);
            net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

            int maxStackSize = Math.min(lens.getMaxStackSize(inv), nativeStack.getMaxStackSize());
            int remaining = stack.getQuantity();

            for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
                net.minecraft.item.ItemStack old = lens.getStack(inv, ord);
                int push = Math.min(remaining, maxStackSize);
                if (old.isEmpty() && lens.setStack(inv, ord, ItemStackUtil.cloneDefensiveNative(nativeStack, push))) {
                    remaining -= push;
                } else if (!old.isEmpty() && ItemStackUtil.compareIgnoreQuantity(old, stack)) {
                    push = Math.max(Math.min(maxStackSize - old.getCount(), remaining), 0); // max() accounts for oversized stacks
                    old.setCount(old.getCount() + push);
                    remaining -= push;
                }
            }

            if (remaining == stack.getQuantity()) {
                // No items were consumed
                result.type(Type.FAILURE).reject(ItemStackUtil.cloneDefensive(nativeStack));
            } else {
                stack.setQuantity(remaining);
            }

            return result.build();
        }

        public static int countStacks(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
            return Logic.countStacks(adapter.getInventory(), adapter.getRootLens());
        }

        public static int countStacks(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
            int stacks = 0;

            for (int ord = 0; ord < lens.slotCount(); ord++) {
                stacks += !lens.getStack(inv, ord).isEmpty() ? 1 : 0;
            }

            return stacks;
        }

        public static int countItems(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
            return Logic.countItems(adapter.getInventory(), adapter.getRootLens());
        }

        public static int countItems(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
            int items = 0;

            for (int ord = 0; ord < lens.slotCount(); ord++) {
                net.minecraft.item.ItemStack stack = lens.getStack(inv, ord);
                items += !stack.isEmpty() ? stack.getCount() : 0;
            }

            return items;
        }

        public static int getCapacity(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
            return Logic.getCapacity(adapter.getInventory(), adapter.getRootLens());
        }

        public static int getCapacity(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
            return lens.getSlots().size();
        }

        public static Collection<InventoryProperty<?, ?>> getProperties(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter,
                Inventory child, Class<? extends InventoryProperty<?, ?>> property) {
            return Logic.getProperties(adapter.getInventory(), adapter.getRootLens(), child, property);
        }

        public static Collection<InventoryProperty<?, ?>> getProperties(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens,
                Inventory child, Class<? extends InventoryProperty<?, ?>> property) {

            if (child instanceof InventoryAdapter) {
                checkNotNull(property, "property");
                int index = lens.getChildren().indexOf(((InventoryAdapter<?, ?>) child).getRootLens());
                if (index > -1) {
                    return lens.getProperties(index).stream().filter(prop -> property.equals(prop.getClass()))
                            .collect(Collectors.toCollection(ArrayList::new));
                }
            }

            return Collections.emptyList();
        }

        static <T extends InventoryProperty<?, ?>> Collection<T> getRootProperties(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, Class<T> property) {
            adapter = inventoryRoot(adapter);
            if (adapter instanceof CustomInventory) {
                return ((CustomInventory) adapter).getProperties().values().stream().filter(p -> property.equals(p.getClass()))
                        .map(property::cast).collect(Collectors.toList());
            }
            return Streams.stream(findRootProperty(adapter, property)).collect(Collectors.toList());
        }

        static <T extends InventoryProperty<?, ?>> Optional<T> getRootProperty(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, Class<T> property, Object key) {
            adapter = inventoryRoot(adapter);
            if (adapter instanceof CustomInventory) {
                InventoryProperty forKey = ((CustomInventory) adapter).getProperties().get(key);
                if (forKey != null && property.equals(forKey.getClass())) {
                    return Optional.of((T) forKey);
                }
            }
            return findRootProperty(adapter, property);
        }

        private static <T extends InventoryProperty<?, ?>> Optional<T> findRootProperty(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, Class<T> property) {
            if (property == InventoryTitle.class) {
                if (adapter instanceof Container) {
                    IInventory inv = adapter.getInventory().allInventories().iterator().next();
                    Text text = SpongeTexts.toText(inv.getDisplayName());
                    return ((Optional<T>) Optional.of(InventoryTitle.of(text)));
                }
                if (adapter instanceof IInventory) {
                    Text text = SpongeTexts.toText(((IInventory) adapter).getDisplayName());
                    return ((Optional<T>) Optional.of(InventoryTitle.of(text)));
                }
            }
            // TODO more properties of top level inventory
            return Optional.empty();
        }

        private static InventoryAdapter<IInventory, net.minecraft.item.ItemStack> inventoryRoot(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
            // Get Root Inventory
            adapter = ((InventoryAdapter) adapter.root());
            if (adapter instanceof Container) {
                // If Root is a Container get the viewed inventory
                IInventory first = adapter.getInventory().allInventories().iterator().next();
                if (first instanceof CustomInventory) {
                    // if viewed inventory is a custom inventory get it instead
                    adapter = ((InventoryAdapter) first);
                }
            }
            return adapter;
        }

        public static boolean contains(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack) {
            return Logic.contains(adapter.getInventory(), adapter.getRootLens(), stack, stack.getQuantity());
        }

        public static boolean contains(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack, int quantity) {
            return Logic.contains(adapter.getInventory(), adapter.getRootLens(), stack, quantity);
        }

        /**
         * Searches for at least <code>quantity</code> of given stack.
         *
         * @param inv The inventory to search in
         * @param lens The lens to search with
         * @param stack The stack to search with
         * @param quantity The quantity to find
         * @return true if at least <code>quantity</code> of given stack has been found in given inventory
         */
        public static boolean contains(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack, int quantity) {
            net.minecraft.item.ItemStack nonNullStack = ItemStackUtil.toNative(stack); // Handle null as empty
            int found = 0;
            for (int ord = 0; ord < lens.slotCount(); ord++) {
                net.minecraft.item.ItemStack slotStack = lens.getStack(inv, ord);
                if (slotStack.isEmpty()) {
                    if (nonNullStack.isEmpty()) {
                        found++; // Found an empty Slot
                        if (found >= quantity) {
                            return true;
                        }
                    }
                } else {
                    if (ItemStackUtil.compareIgnoreQuantity(slotStack, stack)) {
                        found += slotStack.getCount(); // Found a matching stack
                        if (found >= quantity) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public static boolean contains(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemType type) {
            return Logic.contains(adapter.getInventory(), adapter.getRootLens(), type);
        }

        public static boolean contains(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemType type) {
            for (int ord = 0; ord < lens.slotCount(); ord++) {
                net.minecraft.item.ItemStack slotStack = lens.getStack(inv, ord);
                if (slotStack.isEmpty()) {
                    if (type == null || type == ItemTypes.NONE) {
                        return true; // Found an empty Slot
                    }
                } else {
                    if (slotStack.getItem() == type) {
                        return true; // Found a matching stack
                    }
                }
            }
            return false;
        }
    }

    public static final Translation DEFAULT_NAME = new SpongeTranslation("inventory.default.title");

    /**
     * All inventories have their own empty inventory with themselves as the
     * parent. This empty inventory is initialised on-demand but returned for
     * every query which fails. This saves us from creating a new empty
     * inventory with this inventory as the parent for every failed query.
     */
    private EmptyInventory empty;

    @Nullable
    protected Inventory parent;
    protected Inventory next;

    protected final Fabric<IInventory> inventory;
    protected final SlotCollection slots;
    protected final Lens<IInventory, net.minecraft.item.ItemStack> lens;
    protected final List<Inventory> children = new ArrayList<Inventory>();
    protected Iterable<Slot> slotIterator;

    /**
     * Used to calculate {@link #getPlugin()}.
     */
    private final Container rootContainer;

    public Adapter(Fabric<IInventory> inventory, net.minecraft.inventory.Container container) {
        this.inventory = inventory;
        this.parent = this;
        this.slots = this.initSlots(inventory, this);
        this.lens = checkNotNull(this.initRootLens(), "root lens");
        this.rootContainer = (Container) container;
    }

    public Adapter(Fabric<IInventory> inventory, @Nullable Lens<IInventory, net.minecraft.item.ItemStack> root, @Nullable Inventory parent) {
        this.inventory = inventory;
        this.parent = parent == null ? this : parent;
        this.lens = root != null ? root : checkNotNull(this.initRootLens(), "root lens");
        this.slots = this.initSlots(inventory, parent);
        this.rootContainer = null;
    }

    protected SlotCollection initSlots(Fabric<IInventory> inventory, @Nullable Inventory parent) {
        if (parent instanceof InventoryAdapter) {
            @SuppressWarnings("unchecked")
            SlotProvider<IInventory, net.minecraft.item.ItemStack> slotProvider = ((InventoryAdapter<IInventory, net.minecraft.item.ItemStack>)parent).getSlotProvider();
            if (slotProvider instanceof SlotCollection) {
                return (SlotCollection) slotProvider;
            }
        }
        return new SlotCollection(inventory.getSize());
    }

    @Override
    public Inventory parent() {
        return this.parent;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> T first() {
        return (T) this.iterator().next();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> T next() {
        return (T) this.emptyInventory(); // TODO implement me
    }

//    protected Inventory generateParent(Lens<IInventory, net.minecraft.item.ItemStack> root) {
//        Lens<IInventory, net.minecraft.item.ItemStack> parentLens = root.getParent();
//        if (parentLens == null) {
//            return this;
//        }
//        return parentLens.getAdapter(this.inventory);
//    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Lens<IInventory, net.minecraft.item.ItemStack> initRootLens() {
        if (this instanceof LensProvider) {
            return ((LensProvider) this).getRootLens(this.inventory, this);
        }
        int size = this.inventory.getSize();
        if (size == 0) {
            return new DefaultEmptyLens(this);
        }
        return new DefaultIndexedLens(0, size, this, this.slots);
    }

    @Override
    public SlotProvider<IInventory, net.minecraft.item.ItemStack> getSlotProvider() {
        return this.slots;
    }

    @Override
    public Lens<IInventory, net.minecraft.item.ItemStack> getRootLens() {
        return this.lens;
    }

    @Override
    public Fabric<IInventory> getInventory() {
        return this.inventory;
    }

    @Override
    public Inventory getChild(int index) {
        if (index < 0 || index >= this.lens.getChildren().size()) {
            throw new IndexOutOfBoundsException("No child at index: " + index);
        }
        while (index >= this.children.size()) {
            this.children.add(null);
        }
        Inventory child = this.children.get(index);
        if (child == null) {
            child = this.lens.getChildren().get(index).getAdapter(this.inventory, this);
            this.children.set(index, child);
        }
        return child;
    }

    @Override
    public Inventory getChild(Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void notify(Object source, InventoryEventArgs eventArgs) {
    }

    protected final EmptyInventory emptyInventory() {
        if (this.empty == null) {
            this.empty = new EmptyInventoryImpl(this);
        }
        return this.empty;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> Iterable<T> slots() {
        if (this.slotIterator == null) {
            this.slotIterator = this.slots.getIterator(this);
        }
        return (Iterable<T>) this.slotIterator;
    }

    @Override
    public void clear() {
        this.slots().forEach(Inventory::clear);
    }

    public static Optional<Slot> forSlot(Fabric<IInventory> inv, SlotLens<IInventory, net.minecraft.item.ItemStack> slotLens, Inventory parent) {
        return slotLens == null ? Optional.<Slot>empty() : Optional.<Slot>ofNullable((Slot) slotLens.getAdapter(inv, parent));
    }

    @Override
    public PluginContainer getPlugin() {
        if (this.parent != this) {
            return this.parent.getPlugin();
        }
        if (this.rootContainer == null) {
            return null;
        }
        return this.rootContainer.getPlugin();
    }
}
