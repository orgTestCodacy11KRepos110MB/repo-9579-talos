package com.talosvfx.talos.editor.addons.scene.apps.tween.runtime.nodes;

import com.talosvfx.talos.editor.addons.scene.apps.tween.runtime.RoutineNode;
import com.talosvfx.talos.runtime.utils.SimplexNoise;

public class PerlinNoiseNode extends RoutineNode {

    SimplexNoise noise = new SimplexNoise();

    @Override
    public Object queryValue(String targetPortName) {

        float x = fetchFloatValue("x");
        float y = fetchFloatValue("y");

        float scale = fetchFloatValue("scale");

        float query = (noise.query(x * (30f/256f), y * (30f/256f), scale) + 1f)/2f;

        return query;
    }
}