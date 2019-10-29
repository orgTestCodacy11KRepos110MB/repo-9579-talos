package com.rockbite.tools.talos.editor.addons;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.rockbite.tools.talos.editor.addons.bvb.BvBAddon;
import com.rockbite.tools.talos.editor.dialogs.SettingsDialog;

public class AddonController {

    Array<IAddon> activeAddons = new Array();

    public AddonController() {
        registerAddon(new BvBAddon());
    }

    private void registerAddon(IAddon addon) {
        activeAddons.add(addon);
    }

    public void initAll() {
        for(IAddon addon: activeAddons) {
            addon.init();
        }
    }

    public IAddon projectFileDrop(FileHandle handle) {
        for(IAddon addon: activeAddons) {
            boolean accepted = addon.projectFileDrop(handle);
            if(accepted) return addon;
        }

        return null;
    }

    public void announceLocalSettings(SettingsDialog settingsDialog) {
        for(IAddon addon: activeAddons) {
            addon.announceLocalSettings(settingsDialog);
        }
    }
}