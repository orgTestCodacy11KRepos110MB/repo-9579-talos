package com.talosvfx.talos.editor.layouts;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;

import java.util.UUID;

public class DummyLayoutApp implements LayoutApp {

	private String tabName;

	private String uuid;

	private transient Table tabWidget;
	private transient Actor mainContent;
	private transient Skin skin;
	private DestroyCallback destroyCallback;
	private boolean active;

	public DummyLayoutApp (Skin skin, String tabName) {
		this.tabName = tabName;
		build(skin);
		uuid = UUID.randomUUID().toString();
	}

	public void build (Skin skin) {
		this.skin = skin;

		tabWidget = createTab(tabName);
		mainContent = createMainContent();
	}

	private Table createTab (String tabName) {
		Table tab = new Table();
		tab.setTouchable(Touchable.enabled);
		tab.setBackground(skin.getDrawable("tab-bg"));

		tab.padLeft(10);
		tab.padRight(10);
		VisLabel visLabel = new VisLabel(tabName.substring(0, Math.min(10, tabName.length())));
		tab.add(visLabel);

		VisImageButton actor = new VisImageButton(skin.getDrawable("ic-fileset-file-ignore"));
		actor.addListener(new ClickListener() {
			@Override
			public void clicked (InputEvent event, float x, float y) {
				super.clicked(event, x, y);
				if (destroyCallback != null) {
					destroyCallback.onDestroyRequest();
				}
			}
		});
		actor.getStyle().up = null;
		actor.getImage().setScaling(Scaling.fill);
		tab.add(actor).size(16).padLeft(5);

		return tab;
	}

	@Override
	public void setTabActive (boolean active) {
		this.active = active;

		if (active) {
			tabWidget.setBackground(skin.getDrawable("tab-bg"));
		} else {
			tabWidget.setBackground(skin.newDrawable("tab-bg", 0.5f, 0.5f, 0.5f, 1f));
		}
	}

	@Override
	public boolean isTabActive () {
		return active;
	}

	private Actor createMainContent () {
		Table table = new Table();
		table.setBackground(skin.newDrawable("white", 0.2f, 0.2f, 0.2f, 1f));
		return table;
	}

	@Override
	public String getUniqueIdentifier () {
		return uuid;
	}

	@Override
	public void setUniqueIdentifier (String uuid) {
		this.uuid = uuid;
	}

	@Override
	public String getFriendlyName () {
		return tabName;
	}

	@Override
	public Actor getTabWidget () {
		return tabWidget;
	}

	@Override
	public Actor copyTabWidget () {
		return createTab(tabName);
	}

	@Override
	public Actor getMainContent () {
		return mainContent;
	}

	@Override
	public Actor getCopyMainContent () {
		Table table = new Table();
		table.setBackground(skin.newDrawable("white", 0.5f, 0.5f, 0.5f, 1f));
		return table;
	}

	@Override
	public void setDestroyCallback (DestroyCallback destroyCallback) {
		this.destroyCallback = destroyCallback;
	}

	@Override
	public void setScrollFocus () {

	}

	@Override
	public void onInputProcessorAdded () {

	}

	@Override
	public void onInputProcessorRemoved () {

	}
}