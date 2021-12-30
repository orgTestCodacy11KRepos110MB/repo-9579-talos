package com.talosvfx.talos.editor.addons.scene.logic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import com.talosvfx.talos.editor.addons.scene.SceneEditorAddon;
import com.talosvfx.talos.editor.addons.scene.logic.components.IComponent;
import com.talosvfx.talos.editor.widgets.propertyWidgets.EditableLabelWidget;
import com.talosvfx.talos.editor.widgets.propertyWidgets.IPropertyProvider;
import com.talosvfx.talos.editor.widgets.propertyWidgets.LabelWidget;
import com.talosvfx.talos.editor.widgets.propertyWidgets.PropertyWidget;

import java.io.StringWriter;

public class Scene implements GameObjectContainer, Json.Serializable, IPropertyHolder, IPropertyProvider {

    public String path;

    public GameObject root;

    public Scene() {

    }

    public Scene(String path) {
        this.path = path;

        root = new GameObject();
    }

    @Override
    public Array<GameObject> getGameObjects () {
        return root.getGameObjects();
    }

    @Override
    public Iterable<IComponent> getComponents () {
        return null;
    }

    @Override
    public void addGameObject (GameObject gameObject) {
        root.addGameObject(gameObject);
    }

    @Override
    public  Array<GameObject> deleteGameObject (GameObject gameObject) {
        return root.deleteGameObject(gameObject);
    }

    @Override
    public void removeObject (GameObject gameObject) {
        root.removeObject(gameObject);
    }

    @Override
    public void addComponent (IComponent component) {

    }

    @Override
    public boolean hasGOWithName (String name) {
        return root.hasGOWithName(name);
    }

    @Override
    public void clearChildren (Array<GameObject> tmp) {
        root.clearChildren(tmp);
    }

    @Override
    public GameObject getParent () {
        return null;
    }

    @Override
    public void setParent (GameObject gameObject) {
        // do nothing
    }

    @Override
    public String getName () {
        FileHandle fileHandle = Gdx.files.absolute(path);
        return fileHandle.nameWithoutExtension();
    }

    @Override
    public void setName (String name) {
        root.setName(name);
    }

    @Override
    public void write (Json json) {
        json.writeValue("path", path);
    }

    @Override
    public void read (Json json, JsonValue jsonData) {
        path = jsonData.getString("path");
    }

    public void save() {
        FileHandle file = Gdx.files.absolute(path);

        String data = getAsString();

        file.writeString(data, false);
    }

    public String getAsString () {
        try {
            FileHandle file = Gdx.files.absolute(path);
            String name = file.nameWithoutExtension();

            StringWriter stringWriter = new StringWriter();
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            json.setWriter(stringWriter);
            json.getWriter().object();

            json.writeValue("name", name);
            json.writeArrayStart("gameObjects");
            Array<GameObject> gameObjects = getGameObjects();
            if(gameObjects != null) {
                for (GameObject gameObject : gameObjects) {
                    json.writeValue(gameObject);
                }
            }
            json.writeArrayEnd();

            String finalString = stringWriter.toString() + "}";

            return finalString;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return "";
    }

    public void loadFromPath() {
        FileHandle dataFile = Gdx.files.absolute(path);
        loadFromJson(dataFile.readString());
    }

    public void loadFromJson(String data) {
        JsonValue jsonValue = new JsonReader().parse(data);
        Json json = new Json();
        JsonValue gameObjectsJson = jsonValue.get("gameObjects");
        root = new GameObject();
        for(JsonValue gameObjectJson: gameObjectsJson) {
            GameObject gameObject = json.readValue(GameObject.class, gameObjectJson);
            root.addGameObject(gameObject);
        }
    }

    @Override
    public Iterable<IPropertyProvider> getPropertyProviders () {
        Array<IPropertyProvider> list = new Array<>();

        list.add(this);

        return list;
    }

    @Override
    public Array<PropertyWidget> getListOfProperties () {
        Array<PropertyWidget> properties = new Array<>();

        LabelWidget labelWidget = new LabelWidget("Name") {
            @Override
            public String getValue () {
                FileHandle file = Gdx.files.absolute(path);
                String name = file.nameWithoutExtension();
                return name;
            }
        };

        properties.add(labelWidget);

        return properties;
    }

    @Override
    public String getPropertyBoxTitle () {
        return "Scene Properties";
    }

    @Override
    public int getPriority () {
        return 0;
    }
}