package com.talosvfx.talos.editor.addons.scene.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.util.ActorUtils;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.talosvfx.talos.TalosMain;
import com.talosvfx.talos.editor.addons.scene.SceneEditorAddon;
import com.talosvfx.talos.editor.addons.scene.assets.AssetRepository;
import com.talosvfx.talos.editor.addons.scene.assets.GameAsset;
import com.talosvfx.talos.editor.addons.scene.assets.GameAssetType;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import com.talosvfx.talos.editor.addons.scene.logic.IPropertyHolder;
import com.talosvfx.talos.editor.addons.scene.logic.components.ScriptComponent;
import com.talosvfx.talos.editor.addons.scene.utils.importers.AssetImporter;
import com.talosvfx.talos.editor.widgets.propertyWidgets.IPropertyProvider;
import com.talosvfx.talos.editor.widgets.ui.FilteredTree;
import com.talosvfx.talos.editor.widgets.ui.SearchFilteredTree;
import com.talosvfx.talos.editor.widgets.ui.common.SquareButton;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SEPropertyPanel extends PropertyPanel{

    public SEPropertyPanel() {
        super();
    }

    @Override
    public void showPanel (IPropertyHolder target, Iterable<IPropertyProvider> propertyProviders) {
        super.showPanel(target, propertyProviders);

        if(target instanceof GameObject) {
            // add part with custom components

            container.row();

            Table table = new Table();
            Label label = new Label("Add Component", TalosMain.Instance().getSkin());
            SquareButton button = new SquareButton(TalosMain.Instance().getSkin(), label, "Add Component to entity");

            button.addListener(new ClickListener() {
                @Override
                public void clicked (InputEvent event, float x, float y) {
                    CreateComponentPopup createComponentPopup = new CreateComponentPopup();
                    createComponentPopup.show(button, (GameObject)target);
                }
            });

            table.add(button).height(30).growX();

            container.add(button).pad(10).growX();
        }
    }

    private class NameAndCreateTable extends Table {

        private Consumer<String> nameConsumer;
        private final VisTextField textField;
        private final VisLabel label;
        private final VisLabel hint;
        private final Runnable back;

        public NameAndCreateTable (Runnable back) {
            this.back = back;

            bottom();
            defaults().left().bottom();
            defaults().pad(5);

            textField = new VisTextField("");
            label = new VisLabel("");
            hint = new VisLabel("");
            Color color = new Color(Color.YELLOW);
            color.a = 0.8f;
            hint.setColor(color);

            add(label);
            row();
            add(textField).growX();
            row();
            add(hint).growX();
            row();

            VisTextButton backButton = new VisTextButton("Back");
            VisTextButton create = new VisTextButton("Create");

            create.setDisabled(true);

            textField.addListener(new ChangeListener() {
                @Override
                public void changed (ChangeEvent event, Actor actor) {
                    String text = textField.getText();
                    if (text.length() < 3) {
                        create.setDisabled(true);
                    } else {
                        create.setDisabled(false);
                    }
                }
            });

            backButton.addListener(new ClickListener(){
                @Override
                public void clicked (InputEvent event, float x, float y) {
                    super.clicked(event, x, y);
                    back.run();
                }
            });
            create.addListener(new ClickListener(){
                @Override
                public void clicked (InputEvent event, float x, float y) {
                    super.clicked(event, x, y);
                    if (create.isDisabled()) return;

                    nameConsumer.accept(textField.getText());
                }
            });

            Table buttonWrapper = new Table();
            buttonWrapper.center();
            buttonWrapper.defaults().pad(5);
            buttonWrapper.add(backButton);
            buttonWrapper.add(create);
            add(buttonWrapper).growX();
        }

        public void setProperties (String field, String rulesHint, String regexAllowed, Consumer<String> nameConsumer) {
            this.nameConsumer = nameConsumer;

            Pattern pattern = Pattern.compile(regexAllowed);
            label.setText(field);
            hint.setText(rulesHint);
            textField.setTextFieldFilter(new VisTextField.TextFieldFilter() {
                @Override
                public boolean acceptChar (VisTextField textField, char c) {
                    String testString = textField.getText();
                    testString += c;
                    Matcher matcher = pattern.matcher(testString);
                    if (matcher.matches()) {
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private class CreateComponentPopup extends VisWindow {

        private final FilteredTree<Object> tree;
        private InputListener stageListener;

        private GameObject gameObject;

        private SearchFilteredTree searchFilteredTree;
        private NameAndCreateTable nameAndCreateTable;

        private Table container;


        public CreateComponentPopup () {
            super("New Component", "module-list");

            setModal(false);
            setMovable(false);
            setKeepWithinParent(false);
            setKeepWithinStage(false);

            padTop(42);
            padBottom(16);
            padLeft(16);
            padRight(16);

            container = new Table();
            nameAndCreateTable = new NameAndCreateTable(new Runnable() {
                @Override
                public void run () {
                    setToTree();
                }
            });

            tree = new FilteredTree<>(getSkin());
            searchFilteredTree = new SearchFilteredTree<>(getSkin(), tree, null);

            // add list items here
            FilteredTree.Node<Object> scripts = new FilteredTree.Node<>("scripts", new Label("Scripts", getSkin()));
            scripts.setSelectable(false);
            tree.add(scripts);

            Label createScriptLabel = new Label("Create Script > ", getSkin());
            createScriptLabel.setColor(0.6f, 0.9f, 0.9f, 1f);
            FilteredTree.Node<Object> newScript = new FilteredTree.Node<>("createscript", createScriptLabel);
            scripts.add(newScript);


            String rootPath = SceneEditorAddon.get().workspace.getProjectPath();
            FileHandle rootHandle = Gdx.files.absolute(rootPath);
            collectAssets(GameAssetType.SCRIPT, rootHandle, scripts);

            setToTree();

            add(container).width(300).minHeight(300).row();
            add().growY();
            invalidate();
            pack();

            createListeners();
        }

        private void setToTree () {
            container.clearChildren();
            container.add(searchFilteredTree).grow();
        }

        private void setToNameAndCreate (String field, String rulesHint, String regexAllowed, Consumer<String> createComponentNameConsumer) {
            nameAndCreateTable.setProperties(field, rulesHint, regexAllowed, createComponentNameConsumer);
            container.clearChildren();
            container.add(nameAndCreateTable).grow();
        }

        private void collectAssets (GameAssetType assetType, FileHandle handle, FilteredTree.Node<Object> scripts) {
            if (handle.isDirectory()) {
                FileHandle[] list = handle.list();
                for (int i = 0; i < list.length; i++) {
                    FileHandle child = list[i];
                    collectAssets(assetType, child, scripts);
                }
            } else {
                GameAsset<?> assetForPath = AssetRepository.getInstance().getAssetForPath(handle, false);
                if (assetForPath != null) {
                    if (assetForPath.type == assetType) {
                        FilteredTree.Node<Object> scriptNode = new FilteredTree.Node<>(assetForPath.nameIdentifier, new Label(assetForPath.nameIdentifier, getSkin()));
                        scriptNode.setObject(assetForPath);
                        scripts.add(scriptNode);
                    }
                }
            }
        }

        public void show(Actor source, GameObject gameObject) {
            this.gameObject = gameObject;
            Vector2 tmp = new Vector2();
            source.localToStageCoordinates(tmp);

            Stage stage = TalosMain.Instance().UIStage().getStage();
            setPosition(tmp.x, tmp.y - getHeight());
            if (stage.getHeight() - getY() > stage.getHeight()) setY(getY() + getHeight());
            ActorUtils.keepWithinStage(stage, this);
            stage.addActor(this);
        }

        public boolean contains (float x, float y) {
            return getX() < x && getX() + getWidth() > x && getY() < y && getY() + getHeight() > y;
        }

        @Override
        protected void setStage (Stage stage) {
            super.setStage(stage);
            if (stage != null) stage.addListener(stageListener);
        }

        private void createListeners() {
            stageListener = new InputListener() {
                @Override
                public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                    if (!CreateComponentPopup.this.contains(x, y) && button == 0) {
                        remove();
                        return false;
                    }
                    return false;
                }
            };

            tree.addItemListener(new FilteredTree.ItemListener() {
                @Override
                public void selected (FilteredTree.Node node) {
                    super.selected(node);
                    String name = node.getName();


                    if (node.getObject() instanceof GameAsset) {
                        //Make the component and add it
                        GameAsset<?> gameAsset = (GameAsset<?>)node.getObject();

                        if (gameAsset.type == GameAssetType.SCRIPT) {
                            ScriptComponent scriptComponent = new ScriptComponent();
                            scriptComponent.setGameAsset((GameAsset<String>)gameAsset);
                            gameObject.addComponent(scriptComponent);

                            ProjectExplorerWidget projectExplorer = SceneEditorAddon.get().projectExplorer;
                            projectExplorer.reload();

                            SceneEditorAddon.get().propertyPanel.notifyPropertyHolderRemoved(gameObject);
                            SceneEditorAddon.get().workspace.selectPropertyHolder(gameObject);

                            remove();
                            return;
                        }

                    } else {

                        if (name.equals("createscript")) {
                            setToNameAndCreate("Script Name", "Use characters [a-Z] only", "[a-zA-Z]*", new Consumer<String>() {
                                @Override
                                public void accept (String newFileName) {
                                    //Create the script, and then add it to the game component after registering etc etc

                                    FileHandle currentFolder = SceneEditorAddon.get().projectExplorer.getDirectoryViewWidget().getCurrentFolder();

                                    FileHandle newScriptDestination = AssetImporter.suggestNewNameForFileHandle(currentFolder.path(), newFileName, "ts");
                                    FileHandle templateScript = Gdx.files.internal("addons/scene/missing/ScriptTemplate.ts");

                                    String templateString = templateScript.readString();
                                    templateString = templateString.replaceAll("%TEMPLATE_NAME%", newScriptDestination.nameWithoutExtension());
                                    newScriptDestination.writeString(templateString, false);

                                    AssetRepository.getInstance().rawAssetCreated(newScriptDestination, true);

                                    GameAsset<?> assetForPath = AssetRepository.getInstance().getAssetForPath(newScriptDestination, false);

                                    if (assetForPath != null) {

                                        ScriptComponent scriptComponent = new ScriptComponent();
                                        scriptComponent.setGameAsset((GameAsset<String>)assetForPath);
                                        gameObject.addComponent(scriptComponent);


                                        ProjectExplorerWidget projectExplorer = SceneEditorAddon.get().projectExplorer;
                                        projectExplorer.reload();

                                        SceneEditorAddon.get().propertyPanel.notifyPropertyHolderRemoved(gameObject);
                                        SceneEditorAddon.get().workspace.selectPropertyHolder(gameObject);

                                    }

                                    remove();
                                    return;
                                }
                            });
                            return;
                        }
                        //Check all the cases we might otherwise have
                    }
                    remove();
                }
            });
        }
    }
}
