package com.infinityreforged.moderncombat;

import com.infinityreforged.moderncombat.network.PacketOffhandSync;
import com.infinityreforged.moderncombat.network.PacketShieldCooldown;
import com.infinityreforged.moderncombat.network.PacketSound;
import com.infinityreforged.moderncombat.network.PacketSweep;

/**
 * Server-side proxy. Client-only effects are no-ops here.
 */
public class CommonProxy {

    public void preInit() {}

    public void init() {}

    public void handleSweep(PacketSweep message) {}

    public void handleSound(PacketSound message) {}

    public void handleShieldCooldown(PacketShieldCooldown message) {}

    public void handleOffhandSync(PacketOffhandSync message) {}
}
