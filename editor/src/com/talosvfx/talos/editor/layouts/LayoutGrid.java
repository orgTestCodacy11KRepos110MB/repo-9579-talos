package com.talosvfx.talos.editor.layouts;

import ch.qos.logback.core.Layout;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.widget.VisLabel;

import java.util.Objects;

public class LayoutGrid extends WidgetGroup {
	private final DragAndDrop dragAndDrop;

	LayoutItem root;

	LayoutContent overItem;
	LayoutContent startItem;
	private Skin skin;

	private DragHitResult dragHitResult = new DragHitResult();

	private ObjectMap<LayoutContentApp, DragAndDrop.Source> sources = new ObjectMap<>();
	private ObjectMap<LayoutContent, DragAndDrop.Target> targets = new ObjectMap<>();

	float horizontalPercent = 0.3f;
	float verticalPercent = 0.3f;

	float rootHorizontalPercent = 0.03f;
	float rootVerticalPercent = 0.03f;

	public LayoutGrid (Skin skin) {
		this.skin = skin;

		dragAndDrop = new DragAndDrop();
		dragAndDrop.setKeepWithinStage(false);
	}

	public void removeContent (LayoutContent content) {
		removeContent(content, true);
	}

	public void removeContent (LayoutContent content, boolean removeEmptyParent) {
		removeDragTarget(content);

		if (content == root) {
			root = null;
			removeActor(content);
		} else {
			removeRecursive(content, removeEmptyParent);
		}
	}

	private void removeRecursive (LayoutItem content, boolean removeEmpty) {
		LayoutItem parent = (LayoutItem)content.getParent(); //Its always going to be a LayoutItem

		parent.removeItem(content);

		if (removeEmpty && parent.isEmpty()) {
			removeRecursive(parent, removeEmpty);
		}

	}

	public enum LayoutDirection {
		UP,
		RIGHT,
		DOWN,
		LEFT,
		TAB
	}

	public void addContent (LayoutItem content) {
		if (root == null) {
			root = content;
			addActor(root);
		} else {
			if (root instanceof LayoutRow) {
				((LayoutRow)root).addColumnContainer(content, false);
			} else {
				//Exchange root
				LayoutItem oldRoot = root;
				removeActor(oldRoot);

				LayoutRow newRow = new LayoutRow(skin, this);
				newRow.addColumnContainer(oldRoot, false);
				newRow.addColumnContainer(content, false);

				root = newRow;
				addActor(root);
			}
		}

		if (content instanceof LayoutContent) {
			registerDragTarget((LayoutContent)content);
		}

	}

	void registerDragTarget (LayoutContent layoutContent) {
		DragAndDrop.Target target = new DragAndDrop.Target(layoutContent) {
			@Override
			public boolean drag (DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {

				//should just always be true

				return true;
			}

			@Override
			public void drop (DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
				DragHitResult hitResult = dragHitResult;
				if (hitResult.hit == null)
					return;
				if (hitResult.hit == startItem)
					return;

				if (hitResult.root) {
					dropContainer((LayoutContentApp)payload.getObject(), null, dragHitResult.direction);
				} else {
					dropContainer((LayoutContentApp)payload.getObject(), dragHitResult.hit, dragHitResult.direction);
				}

			}
		};
		dragAndDrop.addTarget(target);
		targets.put(layoutContent, target);
	}

	public void removeDragTarget (LayoutContent content) {
		DragAndDrop.Target remove = targets.remove(content);
		dragAndDrop.removeTarget(remove);
	}

	/*
	target is null when root should be used
	 */
	private void dropContainer (LayoutContentApp source, @Null LayoutContent target, LayoutDirection direction) {
		//Here comes the logic

		LayoutContent parent = source.layoutContent;
		String app = source.app;

		//Source
		//Remove drag and drop
		DragAndDrop.Source dragAndDropSource = sources.remove(source);
		dragAndDrop.removeSource(dragAndDropSource);

		System.out.println("Removed source");

		//Potentially removes all the shit from hierarchy if its last one
		parent.removeContent(app);

		if (parent.isEmpty()) {
			System.out.println("Parent is empty, removing content");
			removeRecursive(parent, true);
		}

		if (dragHitResult.root) {
			//Target
			switch (direction) {

			case UP:
			case DOWN: {

				//Check if root is row, if its not wrap
				if (root instanceof LayoutColumn) {
					//Just add it to the column

					LayoutContent newLayoutContent = new LayoutContent(skin, this);
					newLayoutContent.addContent(app);

					((LayoutColumn)root).addRowContainer(newLayoutContent, direction == LayoutDirection.UP);

				} else {
					LayoutItem oldRoot = root;
					removeActor(oldRoot);

					LayoutColumn newColumn = new LayoutColumn(skin, this);

					newColumn.setRelativeWidth(1f);
					newColumn.setRelativeHeight(1f);

					oldRoot.setRelativeWidth(1f);
					oldRoot.setRelativeHeight(1f);

					newColumn.addRowContainer(oldRoot, true);

					LayoutContent newLayoutContent = new LayoutContent(skin, this);

					registerDragTarget(newLayoutContent);


					newLayoutContent.setRandomColour(parent.getRandomColour());
					newLayoutContent.addContent(app);
					newLayoutContent.setRelativeWidth(1f);
					newLayoutContent.setRelativeHeight(1f);

					newColumn.addRowContainer(newLayoutContent, direction == LayoutDirection.UP);
					addActor(newColumn);


					root = newColumn;
				}

			}
			break;

			case RIGHT:
			case LEFT: {

				//Check if root is row, if its not wrap
				if (root instanceof LayoutRow) {
					//Just add it to the column

					LayoutContent newLayoutContent = new LayoutContent(skin, this);
					newLayoutContent.addContent(app);

					((LayoutRow)root).addColumnContainer(newLayoutContent, direction == LayoutDirection.LEFT);

				} else {
					LayoutItem oldRoot = root;
					removeActor(oldRoot);

					LayoutRow newLayoutRow = new LayoutRow(skin, this);

					newLayoutRow.setRelativeWidth(1f);
					newLayoutRow.setRelativeHeight(1f);

					oldRoot.setRelativeWidth(1f);
					oldRoot.setRelativeHeight(1f);

					newLayoutRow.addColumnContainer(oldRoot, true);

					LayoutContent newLayoutContent = new LayoutContent(skin, this);

					registerDragTarget(newLayoutContent);


					newLayoutContent.setRandomColour(parent.getRandomColour());
					newLayoutContent.addContent(app);
					newLayoutContent.setRelativeWidth(1f);
					newLayoutContent.setRelativeHeight(1f);

					newLayoutRow.addColumnContainer(newLayoutContent, direction == LayoutDirection.LEFT);
					addActor(newLayoutRow);


					root = newLayoutRow;
				}
			}

			break;
			default:
				throw new IllegalStateException("Unexpected value: " + direction);
			}

			return;
		}

		//Target
		switch (direction) {

		//If its up or down we get the parent of the target and wrap it in a row, then add our content to the top or bottom
		case UP:
		case DOWN: {
			LayoutItem parentItem = (LayoutItem)target.getParent();

			//Check the parent item for the target. If its already a layout row, we can just add at top or bottom

			LayoutColumn colTarget;

			boolean isExistingColumn = false;
			if (parentItem instanceof LayoutColumn) {
				colTarget = (LayoutColumn)parentItem;
				isExistingColumn = true;
			} else {
				//Its TIME TO WRAP

				LayoutColumn newColumn = new LayoutColumn(skin, this);

				//Remove the target
				exchangeAndWrapToColumn(newColumn, target);

				colTarget = newColumn;

			}

			LayoutContent newLayoutContent = new LayoutContent(skin, this);
			newLayoutContent.setRandomColour(parent.getRandomColour());
			registerDragTarget(newLayoutContent);
			newLayoutContent.addContent(app);
			colTarget.addRowContainer(newLayoutContent, direction == LayoutDirection.UP, isExistingColumn ? target : null);
		}
		break;

		case RIGHT:
		case LEFT: {

			LayoutItem parentItem = (LayoutItem)target.getParent();

			//Check the parent item for the target. If its already a layout row, we can just add at top or bottom

			LayoutRow rowTarget;

			boolean isExistingRow = false;
			if (parentItem instanceof LayoutRow) {
				rowTarget = (LayoutRow)parentItem;
				isExistingRow = true;
			} else {
				//Its TIME TO WRAP

				LayoutRow newRow = new LayoutRow(skin, this);

				//Remove the target
				exchangeAndWrapToRow(newRow, target);

				rowTarget = newRow;

			}

			LayoutContent newLayoutContent = new LayoutContent(skin, this);
			newLayoutContent.setRandomColour(parent.getRandomColour());
			registerDragTarget(newLayoutContent);
			newLayoutContent.addContent(app);
			rowTarget.addColumnContainer(newLayoutContent, direction == LayoutDirection.LEFT, isExistingRow ? target : null);
		}

		break;

		case TAB:

			//If its a tab, we add it to the target
			target.addContent(app);
			break;
		default:
			throw new IllegalStateException("Unexpected value: " + direction);
		}

	}

	private void exchangeAndWrapToColumn (LayoutColumn newColumn, LayoutContent target) {

		//We need to swap this column with the parent
		LayoutItem parent = (LayoutItem)target.getParent();
		parent.exchangeItem(target, newColumn);
		newColumn.setRelativeWidth(target.getRelativeWidth());
		newColumn.setRelativeHeight(target.getRelativeHeight());

		target.setRelativeWidth(1f);
		target.setRelativeHeight(1f);

		newColumn.addRowContainer(target, false);
	}

	private void exchangeAndWrapToRow (LayoutRow newRow, LayoutContent target) {

		//We need to swap this column with the parent
		LayoutItem parent = (LayoutItem)target.getParent();
		parent.exchangeItem(target, newRow);

		newRow.setRelativeWidth(target.getRelativeWidth());
		newRow.setRelativeHeight(target.getRelativeHeight());

		target.setRelativeWidth(1f);
		target.setRelativeHeight(1f);

		newRow.addColumnContainer(target, false);
	}

	//Add each LayoutContent for drag and drop as a source
	//Add each LayoutContent for drag and drop as a target
	void registerDragSource (LayoutContent parent, VisLabel actorToDrag) {

		LayoutContentApp userObject = new LayoutContentApp(parent, actorToDrag.getText().toString());

		DragAndDrop.Source source = new DragAndDrop.Source(actorToDrag) {
			@Override
			public DragAndDrop.Payload dragStart (InputEvent event, float x, float y, int pointer) {
				DragAndDrop.Payload payload = new DragAndDrop.Payload();

				LayoutContent dummy = new LayoutContent(skin, LayoutGrid.this);
				dummy.setRandomColour(parent.getRandomColour());
				dummy.setSize(200, 200);
				dummy.addContent(actorToDrag.getText().toString());

				payload.setDragActor(dummy);
				payload.setObject(userObject);

				startItem = parent;

				return payload;
			}

			@Override
			public void drag (InputEvent event, float x, float y, int pointer) {
				super.drag(event, x, y, pointer);

				float unhitSize = 200;

				Actor dragActor = dragAndDrop.getDragActor();
				if (dragActor != null) {

					//Find out if we hit something, and if so what side

					getDragHit(dragHitResult);

					Vector2 vector2 = new Vector2();
					float hitInStageX = vector2.x;
					float hitInStageY = vector2.y;

					LayoutContent hitResult = dragHitResult.hit;
					if (hitResult != null) {
						if (hitResult == startItem) {
							return;
						}

						float horizontalPercentToUse = dragHitResult.root ? rootHorizontalPercent : horizontalPercent;
						float vertPercentToUse = dragHitResult.root ? rootVerticalPercent : verticalPercent;

						Actor targetActor = dragHitResult.root ? LayoutGrid.this : dragHitResult.hit;

						switch (dragHitResult.direction) {
						case UP:

							dragActor.setSize(targetActor.getWidth(), targetActor.getHeight() * verticalPercent);

							//The offset needs to be the difference between the drag x and y and the target x and y
							vector2.setZero();
							targetActor.localToStageCoordinates(vector2);
							vector2.sub(x, y);

							hitInStageX = vector2.x;
							hitInStageY = vector2.y;

							vector2.set(Gdx.input.getX(), Gdx.input.getY());
							screenToLocalCoordinates(vector2);

							dragAndDrop.setDragActorPosition(x - (vector2.x - hitInStageX) + targetActor.getWidth(), y - (vector2.y - hitInStageY) + targetActor.getHeight() - dragActor.getHeight());

							break;

						case DOWN:
							dragActor.setSize(targetActor.getWidth(), targetActor.getHeight() * verticalPercent);

							//The offset needs to be the difference between the drag x and y and the target x and y
							vector2.setZero();
							targetActor.localToStageCoordinates(vector2);
							vector2.sub(x, y);

							hitInStageX = vector2.x;
							hitInStageY = vector2.y;

							vector2.set(Gdx.input.getX(), Gdx.input.getY());
							screenToLocalCoordinates(vector2);

							dragAndDrop.setDragActorPosition(x - (vector2.x - hitInStageX) + targetActor.getWidth(), y - (vector2.y - hitInStageY));
							break;

						case RIGHT:
							dragActor.setSize(horizontalPercent * targetActor.getWidth(), targetActor.getHeight());

							//The offset needs to be the difference between the drag x and y and the target x and y
							vector2.setZero();
							targetActor.localToStageCoordinates(vector2);
							vector2.sub(x, y);

							hitInStageX = vector2.x;
							hitInStageY = vector2.y;

							vector2.set(Gdx.input.getX(), Gdx.input.getY());
							screenToLocalCoordinates(vector2);

							dragAndDrop.setDragActorPosition(x - (vector2.x - hitInStageX) + targetActor.getWidth(), y - (vector2.y - hitInStageY));
							break;

						case LEFT:
							dragActor.setSize(horizontalPercent * targetActor.getWidth(), targetActor.getHeight());

							//The offset needs to be the difference between the drag x and y and the target x and y
							vector2.setZero();
							targetActor.localToStageCoordinates(vector2);
							vector2.sub(x, y);

							hitInStageX = vector2.x;
							hitInStageY = vector2.y;

							vector2.set(Gdx.input.getX(), Gdx.input.getY());
							screenToLocalCoordinates(vector2);

							dragAndDrop.setDragActorPosition(x - (vector2.x - hitInStageX) + dragActor.getWidth(), y - (vector2.y - hitInStageY));
							break;

						case TAB:
							Table tabTable = hitResult.getTabTable();
							dragActor.setSize(200, tabTable.getHeight());

							vector2.setZero();
							targetActor.localToStageCoordinates(vector2);
							vector2.sub(x, y);

							hitInStageX = vector2.x;
							hitInStageY = vector2.y;

							vector2.set(Gdx.input.getX(), Gdx.input.getY());
							screenToLocalCoordinates(vector2);

							dragAndDrop.setDragActorPosition(+dragActor.getWidth() / 2f, y - (vector2.y - hitInStageY) + targetActor.getHeight() - dragActor.getHeight());

							break;
						}
					} else {
						dragActor.setSize(unhitSize, unhitSize);
						dragAndDrop.setDragActorPosition(dragActor.getWidth() / 2f, -dragActor.getHeight() / 2f);
					}

				}

			}

			@Override
			public void dragStop (InputEvent event, float x, float y, int pointer, DragAndDrop.Payload payload, DragAndDrop.Target target) {
				super.dragStop(event, x, y, pointer, payload, target);
				startItem = null;
				dragHitResult.reset();
			}
		};
		dragAndDrop.addSource(source);

		sources.put(userObject, source);
	}

	private void getDragHit (DragHitResult dragHitResult) {
		dragHitResult.reset();

		int x = Gdx.input.getX();
		int y = Gdx.input.getY();
		Vector2 coords = new Vector2(x, y);

		//Check for root
		if (root != null) {
			//Check edges of root
			Vector2 vecForMainGrid = new Vector2();
			vecForMainGrid.set(coords);
			screenToLocalCoordinates(vecForMainGrid);

			vecForMainGrid.scl(1f / getWidth(), 1f / getHeight());

			float distanceFromMiddleX = Math.abs(0.5f - vecForMainGrid.x);
			float distanceFromMiddleY = Math.abs(0.5f - vecForMainGrid.y);

			if (distanceFromMiddleX >= distanceFromMiddleY) {
				//Its going to be an X if it exists

				if (vecForMainGrid.x < rootHorizontalPercent) {
					//Left edge
					dragHitResult.root = true;
					dragHitResult.hit = overItem;
					dragHitResult.direction = LayoutDirection.LEFT;
					return;
				} else if (vecForMainGrid.x > (1 - rootHorizontalPercent)) {
					//Right edge
					dragHitResult.root = true;
					dragHitResult.hit = overItem;
					dragHitResult.direction = LayoutDirection.RIGHT;
					return;
				}


			} else {
				//its going to be Y if it exists
				if (vecForMainGrid.y < rootVerticalPercent) {
					dragHitResult.root = true;
					dragHitResult.hit = overItem;
					dragHitResult.direction = LayoutDirection.DOWN;
					return;
				} else if (vecForMainGrid.y > (1 - rootVerticalPercent)) {
					//top edge
					dragHitResult.root = true;
					dragHitResult.hit = overItem;
					dragHitResult.direction = LayoutDirection.UP;
					return;
				}
			}
		}

		if (overItem == null) {
			return;
		}

		overItem.screenToLocalCoordinates(coords);

		Vector2 localCoords = new Vector2(coords.x, coords.y);

		coords.scl(1f / overItem.getWidth(), 1f / overItem.getHeight());

		//UNiversal coordinates

		float distanceFromMiddleX = Math.abs(0.5f - coords.x);
		float distanceFromMiddleY = Math.abs(0.5f - coords.y);

		if (distanceFromMiddleX >= distanceFromMiddleY) {
			//Its going to be an X if it exists

			if (coords.x < horizontalPercent) {
				//Left edge
				dragHitResult.hit = overItem;
				dragHitResult.direction = LayoutDirection.LEFT;
			} else if (coords.x > (1 - horizontalPercent)) {
				//Right edge
				dragHitResult.hit = overItem;
				dragHitResult.direction = LayoutDirection.RIGHT;
			}

		} else {
			//its going to be Y if it exists
			if (coords.y < verticalPercent) {

				dragHitResult.hit = overItem;
				dragHitResult.direction = LayoutDirection.DOWN;
			} else if (coords.y > (1 - verticalPercent)) {
				//top edge

				boolean hitLabel = false;

				Actor hit = null;
				if ((hit = ((LayoutContent)overItem).hitTabTable(localCoords)) != null) {
					dragHitResult.hit = overItem;
					dragHitResult.direction = LayoutDirection.TAB;
					hitLabel = true;
				}

				if (!hitLabel) {
					dragHitResult.hit = overItem;
					dragHitResult.direction = LayoutDirection.UP;
				}
			}
		}

	}

	static class LayoutContentApp {
		public LayoutContent layoutContent;
		public String app;

		public LayoutContentApp (LayoutContent parent, String app) {
			this.layoutContent = parent;
			this.app = app;
		}

		@Override
		public boolean equals (Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			LayoutContentApp that = (LayoutContentApp)o;
			return Objects.equals(layoutContent, that.layoutContent) && Objects.equals(app, that.app);
		}

		@Override
		public int hashCode () {
			return Objects.hash(layoutContent, app);
		}
	}

	static class DragHitResult {
		public LayoutContent hit;
		public boolean root;
		public LayoutDirection direction;

		void reset () {
			hit = null;
			root = false;
			direction = null;
		}
	}

	@Override
	public void layout () {
		super.layout();
		if (root == null)
			return;

		root.setBounds(0, 0, getWidth(), getHeight());
	}

	@Override
	public void act (float delta) {
		super.act(delta);
		if (root == null) {
			return;
		}

		int x = Gdx.input.getX();
		int y = Gdx.graphics.getHeight() - Gdx.input.getY();
		Vector2 screenCoords = new Vector2(x, y);
		screenToLocalCoordinates(screenCoords);

		Actor hit = hit(x, y, true);
		if (hit instanceof LayoutContent) {
			overItem = (LayoutContent)hit;
		} else if (hit != null) {
			if (hit.getParent() instanceof LayoutContent) {
				overItem = (LayoutContent)hit.getParent();
			} else if (hit.getParent() != null) {
				if (hit.getParent().getParent() instanceof LayoutContent) {
					overItem = (LayoutContent)hit.getParent().getParent();
				}
			}
		} else {
			overItem = null;
		}
	}

}
