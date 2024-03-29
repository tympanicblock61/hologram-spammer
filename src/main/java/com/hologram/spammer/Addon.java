package com.hologram.spammer;

import com.hologram.spammer.commands.HoloImage;
import com.hologram.spammer.modules.HologramSpammer;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    @Override
    public void onInitialize() {
        Modules.get().add(new HologramSpammer());
        Commands.add(new HoloImage());
        LOG.info("initialized hologram spammer");
    }
    @Override
    public String getPackage() {
        return "com.hologram.spammer";
    }
}
