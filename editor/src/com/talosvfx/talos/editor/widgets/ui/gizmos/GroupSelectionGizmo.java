package com.talosvfx.talos.editor.widgets.ui.gizmos;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import com.talosvfx.talos.editor.addons.scene.logic.components.SpriteRendererComponent;
import com.talosvfx.talos.editor.addons.scene.logic.components.TransformComponent;
import com.talosvfx.talos.editor.addons.scene.widgets.gizmos.Gizmo;
import com.talosvfx.talos.editor.widgets.ui.ViewportWidget;
import com.talosvfx.talos.editor.widgets.ui.common.ColorLibrary;

import java.util.Comparator;

public class GroupSelectionGizmo extends Gizmo {

	private final ViewportWidget viewportWidget;

	private BoundingBox selectionBounds = new BoundingBox();

	private ObjectMap<GameObject, Vector2> worldSpaceStartingOffsets = new ObjectMap<>();
	private Comparator<GameObject> parentHierarchySorter;

	public GroupSelectionGizmo (ViewportWidget widget) {
		this.viewportWidget = widget;

		parentHierarchySorter = new Comparator<GameObject>() {
			@Override
			public int compare (GameObject o1, GameObject o2) {
				int o1Count = o1.getParentCount();
				int o2Count = o2.getParentCount();
				return Integer.compare(o1Count, o2Count);
			}
		};
	}

	@Override
	public void act (float delta) {
		super.act(delta);

		calculateSelectionBounds();
	}

	@Override
	public boolean hit (float x, float y) {
		return selectionBounds.contains(new Vector3(x, y, 0));
	}

	@Override
	public void setSelected (boolean selected) {
		super.setSelected(selected);

	}

	@Override
	public void touchDown (float x, float y, int button) {
		super.touchDown(x, y, button);

		worldSpaceStartingOffsets.clear();

		//Sort the selection by parent hierarchy becuase its impportant to do parents first

		Array<GameObject> gameObjects = viewportWidget.selection.orderedItems();

		gameObjects.sort(parentHierarchySorter);
		for (GameObject object : gameObjects) {
			if (object.hasComponent(TransformComponent.class)) {
				TransformComponent transformComponent = object.getComponent(TransformComponent.class);
				Vector2 worldPosition = transformComponent.worldPosition;

				worldSpaceStartingOffsets.put(object, new Vector2().set(x, y).sub(worldPosition));
			}
		}

	}

	@Override
	public void touchDragged (float x, float y) {
		super.touchDragged(x, y);

		for (GameObject object : viewportWidget.selection) {
			if (object.hasComponent(TransformComponent.class)) {
				Vector2 worldSpaceOffset = worldSpaceStartingOffsets.get(object);

				Vector2 newWorldSpace = new Vector2(x, y).sub(worldSpaceOffset);

				GameObject.setPositionFromWorldPosition(object, newWorldSpace);
			}
		}
	}

	@Override
	public void touchUp (float x, float y) {

	}

	private void calculateSelectionBounds () {
		selectionBounds.clr();

		boolean first = true;

		for (GameObject object : viewportWidget.selection) {
			//Do da bounds on the transforms or widgets



			if (object.hasComponent(TransformComponent.class)) {

				TransformComponent transformComponent = object.getComponent(TransformComponent.class);
				if (first) {
					first = false;
					Vector3 minMaxDefault = new Vector3(transformComponent.worldPosition.x, transformComponent.worldPosition.y, 0);
					selectionBounds.set(minMaxDefault, minMaxDefault);
				} else {
					selectionBounds.ext(transformComponent.worldPosition.x, transformComponent.worldPosition.y, 0);

				}




				if (object.hasComponent(SpriteRendererComponent.class)) {
					SpriteRendererComponent spriteRendererComponent = object.getComponent(SpriteRendererComponent.class);
					float x = transformComponent.worldPosition.x;
					float y = transformComponent.worldPosition.y;

					Vector2 spriteSize = spriteRendererComponent.size;
					float spriteHeight = spriteSize.y * transformComponent.worldScale.x;
					float spriteWidth = spriteSize.x * transformComponent.worldScale.y;
					float halfWidth = spriteWidth / 2;
					float halfHeight = spriteHeight / 2;

					selectionBounds.ext(x - halfWidth, y - halfHeight, 0);
					selectionBounds.ext(x - halfWidth, y + halfHeight, 0);
					selectionBounds.ext(x + halfWidth, y - halfHeight, 0);
					selectionBounds.ext(x + halfWidth, y + halfHeight, 0);
				}
			}

		}
	}

	@Override
	public void draw (Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);

		if (selected && !viewportWidget.selection.isEmpty()) {

			float boundsWidth = selectionBounds.getWidth();
			float boundsHeight = selectionBounds.getHeight();
			float centerX = selectionBounds.getCenterX();
			float centerY = selectionBounds.getCenterY();
			drawCircle(new Vector2(centerX, centerY), batch);

			drawLine(batch, centerX - boundsWidth/2, centerY - boundsHeight/2, centerX - boundsWidth/2, centerY + boundsHeight/2, ColorLibrary.ORANGE);
			drawLine(batch, centerX + boundsWidth/2, centerY - boundsHeight/2, centerX + boundsWidth/2, centerY + boundsHeight/2, ColorLibrary.ORANGE);

			drawLine(batch, centerX - boundsWidth/2, centerY + boundsHeight/2, centerX + boundsWidth/2, centerY + boundsHeight/2, ColorLibrary.ORANGE);
			drawLine(batch, centerX - boundsWidth/2, centerY - boundsHeight/2, centerX + boundsWidth/2, centerY - boundsHeight/2, ColorLibrary.ORANGE);
		}
	}

	public float getCenterX () {
		return selectionBounds.getCenterX();
	}

	public float getCenterY () {
		return selectionBounds.getCenterY();
	}


	@Override
	protected void updateFromGameObject () {

	}

	@Override
	public GameObject getGameObject () {
		throw new GdxRuntimeException("Not supported for Mega gizmo");
	}

	@Override
	public boolean isControllingGameObject (GameObject gameObject) {
		return viewportWidget.selection.contains(gameObject) && viewportWidget.selection.size != 1;
	}

	@Override
	public int getPriority () {
		return -1;
	}
}
