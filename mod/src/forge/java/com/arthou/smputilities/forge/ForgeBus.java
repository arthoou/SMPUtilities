package com.arthou.smputilities.forge;

import java.util.function.Consumer;
import java.util.function.Predicate;
//#if MC>=1.21.6
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.fml.event.IModBusEvent;
//#else
//$$ import net.minecraftforge.common.MinecraftForge;
//$$ import net.minecraftforge.eventbus.api.Event;
//$$ import net.minecraftforge.eventbus.api.EventPriority;
//$$ import net.minecraftforge.eventbus.api.IEventBus;
//#endif

/**
 * Listener registration that reads the same on every Forge version: until
 * 1.21.5 events go through {@code MinecraftForge.EVENT_BUS}, from 1.21.6
 * (EventBus 7) every event class has its own static {@code BUS}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class ForgeBus {
    //#if MC>=1.21.6
    private static BusGroup modBus;

    static void init(BusGroup group) {
        modBus = group;
    }

    private static Object bus(Class<?> type) {
        try {
            return type.getDeclaredField("BUS").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No event bus on " + type.getName(), e);
        }
    }
    //#else
    //$$ private static IEventBus modBus;
    //$$
    //$$ static void init(IEventBus bus) {
    //$$     modBus = bus;
    //$$ }
    //#endif

    private ForgeBus() {
    }

    /** A game event. */
    static <T> void on(Class<T> type, Consumer<T> listener) {
        //#if MC>=1.21.6
        Object bus = bus(type);
        if (bus instanceof CancellableEventBus cancellable) {
            cancellable.addListener(listener);
        } else {
            ((EventBus) bus).addListener(listener);
        }
        //#else
        //$$ MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) type, (Consumer) listener);
        //#endif
    }

    /** A cancellable game event; the test returns true to cancel it. */
    static <T> void cancelIf(Class<T> type, Predicate<T> test) {
        //#if MC>=1.21.6
        ((CancellableEventBus) bus(type)).addListener(test);
        //#else
        //$$ on(type, event -> {
        //$$     if (test.test(event)) {
        //$$         ((Event) event).setCanceled(true);
        //$$     }
        //$$ });
        //#endif
    }

    /** A mod lifecycle or registration event. */
    static <T> void onMod(Class<T> type, Consumer<T> listener) {
        //#if MC>=1.21.6
        IModBusEvent.getBus(modBus, (Class) type).addListener(listener);
        //#else
        //$$ modBus.addListener(EventPriority.NORMAL, false, (Class) type, (Consumer) listener);
        //#endif
    }
}
