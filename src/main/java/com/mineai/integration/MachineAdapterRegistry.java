package com.mineai.integration;

import com.mineai.integration.vanilla.VanillaContainerAdapter;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapter discovery. Adapters are registered explicitly, never by class-name
 * string matching. Optional-mod adapters must be guarded by {@link ModList}.
 */
public final class MachineAdapterRegistry {

    private static final List<MachineAdapter> ADAPTERS = new ArrayList<>();
    private static boolean bootstrapped;

    private MachineAdapterRegistry() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(new VanillaContainerAdapter());
        // Capability-first: covers Mekanism and any other modded machine that
        // exposes a Forge IItemHandler, without mod-specific code.
        register(new CapabilityMachineAdapter());

        // Mods with no item capability need their own adapter, for example:
        // if (ModList.get().isLoaded("mekanism")) {
        //     register(new MekanismGasAdapter());
        // }
    }

    public static void register(MachineAdapter adapter) {
        ADAPTERS.add(adapter);
    }

    public static Optional<MachineAdapter> find(MachineContext context) {
        bootstrap();
        return ADAPTERS.stream().filter(adapter -> adapter.supports(context)).findFirst();
    }

    public static List<MachineAdapter> all() {
        bootstrap();
        return List.copyOf(ADAPTERS);
    }
}
