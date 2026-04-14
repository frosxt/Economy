package com.github.frosxt.economy.bootstrap;

import com.github.frosxt.prisoncore.api.module.ModuleBootstrap;
import com.github.frosxt.prisoncore.api.module.ModuleContext;
import com.github.frosxt.prisoncore.api.module.ModuleLoadPhase;
import com.github.frosxt.prisoncore.api.module.PlatformModule;
import com.github.frosxt.prisoncore.api.module.annotation.ModuleDefinition;

@ModuleDefinition(
        id = "economy",
        name = "Economy",
        version = "1.0.0",
        apiVersion = "1.0",
        loadPhase = ModuleLoadPhase.POST_INFRASTRUCTURE,
        requiredDependencies = {},
        optionalDependencies = {},
        providesCapabilities = {"economy-service", "currency-service"},
        requiresCapabilities = {}
)
public final class EconomyBootstrap implements ModuleBootstrap {

    @Override
    public PlatformModule create(final ModuleContext context) {
        return new EconomyModule();
    }
}
