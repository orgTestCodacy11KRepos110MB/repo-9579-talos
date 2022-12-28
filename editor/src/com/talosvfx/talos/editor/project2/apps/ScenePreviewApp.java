package com.talosvfx.talos.editor.project2.apps;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.talosvfx.talos.editor.addons.scene.apps.routines.ScenePreviewStage;
import com.talosvfx.talos.editor.addons.scene.assets.GameAsset;
import com.talosvfx.talos.editor.addons.scene.logic.Scene;
import com.talosvfx.talos.editor.layouts.DummyLayoutApp;
import com.talosvfx.talos.editor.notifications.Notifications;
import com.talosvfx.talos.editor.notifications.Observer;
import com.talosvfx.talos.editor.project2.AppManager;
import com.talosvfx.talos.editor.project2.SharedResources;
import lombok.Getter;

public class ScenePreviewApp extends AppManager.BaseApp<Scene> implements Observer {

    @Getter
    private final ScenePreviewStage workspaceWidget;

    public ScenePreviewApp() {
        this.singleton = true;

        workspaceWidget = new ScenePreviewStage();
        workspaceWidget.disableListeners();

        DummyLayoutApp sceneEditorWorkspaceApp = new DummyLayoutApp(SharedResources.skin, getAppName()) {
            @Override
            public Actor getMainContent () {
                return workspaceWidget;
            }

            @Override
            public void onInputProcessorAdded () {
                super.onInputProcessorAdded();
                workspaceWidget.restoreListeners();
                SharedResources.stage.setScrollFocus(workspaceWidget);
            }

            @Override
            public void onInputProcessorRemoved () {
                super.onInputProcessorRemoved();
                workspaceWidget.disableListeners();
            }
        };

        this.gridAppReference = sceneEditorWorkspaceApp;
    }

    @Override
    public void updateForGameAsset(GameAsset<Scene> gameAsset) {
        super.updateForGameAsset(gameAsset);

        workspaceWidget.setFromGameAsset(gameAsset);
    }

    @Override
    public String getAppName() {
        return "Preview (Scene)";
    }

    @Override
    public void onRemove() {
        Notifications.unregisterObserver(this);
    }
}