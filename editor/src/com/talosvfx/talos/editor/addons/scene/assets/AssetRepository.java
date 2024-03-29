
package com.talosvfx.talos.editor.addons.scene.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.talosvfx.talos.editor.addons.scene.SceneEditorWorkspace;
import com.talosvfx.talos.editor.addons.scene.events.AssetPathChanged;
import com.talosvfx.talos.editor.addons.scene.events.ComponentUpdated;
import com.talosvfx.talos.editor.addons.scene.events.ScriptFileChangedEvent;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import com.talosvfx.talos.editor.addons.scene.logic.Prefab;
import com.talosvfx.talos.editor.addons.scene.logic.Scene;
import com.talosvfx.talos.editor.addons.scene.logic.TilePaletteData;
import com.talosvfx.talos.editor.addons.scene.logic.components.AComponent;
import com.talosvfx.talos.editor.addons.scene.logic.components.GameResourceOwner;
import com.talosvfx.talos.editor.addons.scene.logic.components.MapComponent;
import com.talosvfx.talos.editor.addons.scene.logic.components.ScriptComponent;
import com.talosvfx.talos.editor.addons.scene.utils.AMetadata;
import com.talosvfx.talos.editor.addons.scene.utils.importers.AssetImporter;
import com.talosvfx.talos.editor.addons.scene.utils.metadata.DirectoryMetadata;
import com.talosvfx.talos.editor.addons.scene.utils.metadata.ScriptMetadata;
import com.talosvfx.talos.editor.addons.scene.utils.metadata.SpineMetadata;
import com.talosvfx.talos.editor.notifications.EventHandler;
import com.talosvfx.talos.editor.notifications.Notifications;
import com.talosvfx.talos.editor.utils.NamingUtils;
import com.talosvfx.talos.runtime.ParticleEffectDescriptor;
import com.talosvfx.talos.runtime.assets.AssetProvider;
import com.talosvfx.talos.runtime.serialization.ExportData;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.talosvfx.talos.editor.addons.scene.utils.importers.AssetImporter.relative;
import static com.talosvfx.talos.editor.project.TalosProject.exportTLSDataToP;
import static com.talosvfx.talos.editor.serialization.ProjectSerializer.writeTalosPExport;

public class AssetRepository implements Notifications.Observer {

	public static final AssetNameFieldFilter ASSET_NAME_FIELD_FILTER = new AssetNameFieldFilter();

	private ObjectMap<GameAssetType, ObjectMap<String, GameAsset<?>>> identifierGameAssetMap = new ObjectMap<>();

	public <T> GameAsset<T> getAssetForIdentifier (String identifier, GameAssetType type) {
		if (identifierGameAssetMap.containsKey(type)) {
			if (identifierGameAssetMap.get(type).containsKey(identifier)) {
				return (GameAsset<T>)identifierGameAssetMap.get(type).get(identifier);
			}
		}
		GameAsset<T> brokenAsset = new GameAsset<>(identifier, type);
		brokenAsset.setBroken(new Exception("No asset found"));
		return brokenAsset;
	}

	private <T> void putAssetForIdentifier (String identifier, GameAssetType type, GameAsset<T> asset) {
		if (!identifierGameAssetMap.containsKey(type)) {
			identifierGameAssetMap.put(type, new ObjectMap<>());
		}
		identifierGameAssetMap.get(type).put(identifier, asset);
	}


	static class DataMaps {
		private ObjectMap<FileHandle, GameAsset> fileHandleGameAssetObjectMap = new ObjectMap<>();
		private ObjectMap<UUID, RawAsset> uuidRawAssetMap = new ObjectMap<>();
		private ObjectMap<FileHandle, RawAsset> fileHandleRawAssetMap = new ObjectMap<>();


		void putFileHandleGameAsset (FileHandle handle, GameAsset<?> gameAsset) {
			this.fileHandleGameAssetObjectMap.put(handle, gameAsset);

			System.out.println("Put file handle game asset " + handle.path() + " " + gameAsset.nameIdentifier);
		}

		void putUUIRawAsset (UUID uuid, RawAsset rawAsset) {
			uuidRawAssetMap.put(uuid, rawAsset);

			System.out.println("Put uuid " + uuid.toString() + " " + rawAsset.handle.path());
		}
		void putFileHandleRawAsset (FileHandle handle, RawAsset rawAsset) {
			fileHandleRawAssetMap.put(handle, rawAsset);

			System.out.println("Put file handle raw " + handle.path() + " " + rawAsset.handle.path());

		}

		public GameAsset removeFileHandleGameAssetObjectMap (FileHandle handle) {
			System.out.println("Removing file handle game asset " + handle.path());
			return fileHandleGameAssetObjectMap.remove(handle);
		}

		public RawAsset removeFileHandleRawAsset (FileHandle handle) {
			System.out.println("Removing file handle raw asset " + handle.path());
			return fileHandleRawAssetMap.remove(handle);
		}

		public RawAsset removeUUIDRawAsset (UUID uuid) {

			System.out.println("Removing uuid raw asset " + uuid.toString());
			return uuidRawAssetMap.remove(uuid);
		}
	}

	private DataMaps dataMaps = new DataMaps();

	private FileHandle assetsRoot;
	private Json json;

	public TextureRegion brokenTextureRegion;

	static AssetRepository instance;
	public static AssetRepository getInstance() {
		if(instance == null) init();
		return instance;
	}
	public static void init () {
		AssetRepository assetRepository = new AssetRepository();
		Notifications.registerObserver(assetRepository);
		AssetRepository.instance = assetRepository;
	}

	public AssetRepository () {
		json = new Json();
		json.setOutputType(JsonWriter.OutputType.json);

		brokenTextureRegion = new TextureRegion(new Texture(Gdx.files.internal("addons/scene/missing/missing.png")));
	}

	public void loadAssetsForProject (FileHandle assetsRoot) {
		this.assetsRoot = assetsRoot;


		//Go over all files, create raw assets if they don't exist in the map
		if (this.assetsRoot.isDirectory()) {
			collectRawResourceFromDirectory(this.assetsRoot, false);
		}

		//Go over all raw assets, and create game resources
		//Game resources need to be able to search for the raw assets to link

		checkAllGameAssetCreation();
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run () {
				loadChangesFromScripts(AssetRepository.this::fileVisit);
			}
		});
	}

	private void loadChangesFromScripts(Function<Path, FileVisitResult> function) {

		FileHandle exportedScriptsFolderHandle = getExportedScriptsFolderHandle();
		if (!exportedScriptsFolderHandle.exists()) {
			return;
		}
		try {
			Path exportPath = exportedScriptsFolderHandle.file().toPath();
			Files.walkFileTree(exportPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException {
					return function.apply(file);
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public FileVisitResult fileVisit (Path dir) {
		File file = dir.toFile();
		if (file.isDirectory()) {
			return FileVisitResult.CONTINUE;
		}
		FileHandle fileHandle = new FileHandle(file);
		if (fileHandle.extension().equals("meta")) {
			return FileVisitResult.CONTINUE;
		}

		if (fileHandle.extension().equals("ts")) {
			// Found a script
			Notifications.fireEvent(Notifications.obtainEvent(ScriptFileChangedEvent.class).set(StandardWatchEventKinds.ENTRY_MODIFY, fileHandle));
		}
		return FileVisitResult.CONTINUE;
	}


	private void checkAllGameAssetCreation () { //raws
		checkGameAssetCreation(GameAssetType.SPRITE);
		checkGameAssetCreation(GameAssetType.SCRIPT);
		checkGameAssetCreation(GameAssetType.TWEEN);
		checkGameAssetCreation(GameAssetType.ATLAS);
		checkGameAssetCreation(GameAssetType.SOUND);

		checkGameAssetCreation(GameAssetType.SKELETON);

		checkGameAssetCreation(GameAssetType.VFX);
		checkGameAssetCreation(GameAssetType.PREFAB);
		checkGameAssetCreation(GameAssetType.SCENE);

		checkGameAssetCreation(GameAssetType.TILE_PALETTE);
	}

	private void checkGameAssetCreation (GameAssetType type) {
		//We need to do multiple passes here for dependent assets

		for (ObjectMap.Entry<FileHandle, RawAsset> entry : dataMaps.fileHandleRawAssetMap) {
			FileHandle key = entry.key;
			RawAsset value = entry.value;


			if (key.isDirectory()) continue;

			try {
				GameAssetType assetTypeFromExtension = GameAssetType.getAssetTypeFromExtension(key.extension());
				if (type != assetTypeFromExtension)
					continue;

				if (assetTypeFromExtension.isRootGameAsset()) {
					createGameAsset(key, value);
				}
			} catch (GameAssetType.NoAssetTypeException e) {
				//Its not an asset
			}
		}
	}

	public void reloadGameAssetForRawFile (RawAsset link) {
		Array<GameAsset> gameAssetReferences = new Array<>();
		gameAssetReferences.addAll(link.gameAssetReferences);

		for (GameAsset gameAssetReference : gameAssetReferences) {
			reloadGameAsset(gameAssetReference);
		}
	}

	private void reloadGameAsset (GameAsset gameAssetReference) {
		RawAsset rootRawAsset = gameAssetReference.getRootRawAsset();
		String gameAssetIdentifier = getGameAssetIdentifierFromRawAsset(rootRawAsset);


		try {
			GameAssetType assetTypeFromExtension = GameAssetType.getAssetTypeFromExtension(rootRawAsset.handle.extension());

			GameAsset gameAsset = createGameAssetForType(assetTypeFromExtension, gameAssetIdentifier, rootRawAsset, false);
			gameAssetReference.setResourcePayload(gameAsset.getResource());
			gameAssetReference.setUpdated();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	static class TypeIdentifierPair {
		GameAssetType type;
		String identifier;

		public TypeIdentifierPair (GameAssetType type, String gameResourceIdentifier) {
			this.type = type;
			this.identifier = gameResourceIdentifier;
		}

		@Override
		public boolean equals (Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			TypeIdentifierPair that = (TypeIdentifierPair)o;
			return type == that.type && identifier.equals(that.identifier);
		}

		@Override
		public int hashCode () {
			return Objects.hash(type, identifier);
		}
	}

	private void collectExportedAssetsArrayGameObjects (JsonValue gameObjects, ObjectSet<TypeIdentifierPair> pairs) {
		if (gameObjects != null) {
			for (JsonValue gameObject : gameObjects) {
				//Grab each component
				collectGameObjectExportedAssets(pairs, gameObject);
			}
		}
	}

	private void collectGameObjectExportedAssets (ObjectSet<TypeIdentifierPair> pairs, JsonValue gameObject) {
		if (gameObject.has("components")) {
			JsonValue components = gameObject.get("components");
			for (JsonValue component : components) {
				String componentClazz = component.getString("class");
				if (componentIsResourceOwner(componentClazz)) {
					//Lets grab the game resource

					GameAssetType type = getGameAssetTypeFromClazz(componentClazz);

					String gameResourceIdentifier = GameResourceOwner.readGameResourceFromComponent(component);
					pairs.add(new TypeIdentifierPair(type, gameResourceIdentifier));
				}
				if (componentClazz.equals(MapComponent.class.getName())) {
					JsonValue layers = component.get("layers");

					for (int i = 0; i < layers.size; i++) {
						JsonValue layer = layers.get(i);
						String gameResourceIdentifier = GameResourceOwner.readGameResourceFromComponent(layer);
						pairs.add(new TypeIdentifierPair(GameAssetType.TILE_PALETTE, gameResourceIdentifier));
					}

				}
			}
		}
		if (gameObject.has("children")) {
			JsonValue children = gameObject.get("children");
			collectExportedAssetsArrayGameObjects(children, pairs);
		}
	}

	//Export formats
	public void exportToFile () { //todo
		//Go over all entities, go over all components. If component has a game resource, we mark it for export

		FileHandle scenes = Gdx.files.absolute(SceneEditorWorkspace.getInstance().getProjectPath()).child("scenes");
		ObjectSet<TypeIdentifierPair> identifiersBeingUsedByComponents = new ObjectSet<>();
		if (scenes.exists()) {
			for (FileHandle handle : scenes.list()) {
				JsonValue scene = new JsonReader().parse(handle);

				JsonValue gameObjects = scene.get("gameObjects");
				collectExportedAssetsArrayGameObjects(gameObjects, identifiersBeingUsedByComponents);
			}
		}

		//Find all prefabs
		FileHandle assets = Gdx.files.absolute(SceneEditorWorkspace.getInstance().getProjectPath()).child("assets");
		//Find all prefabs and do same shit as above, export the prefab GameAsset as well as the .prefabs
		findAllPrefabs(assets, identifiersBeingUsedByComponents);


		Array<GameAsset<?>> gameAssetsToExport = new Array<>();
		for (TypeIdentifierPair identPair : identifiersBeingUsedByComponents) {

			//we need to get the identifier and type pairs

			GameAsset<?> gameAsset = getAssetForIdentifier(identPair.identifier, identPair.type);
			if (gameAsset == null) {
				System.out.println("Game asset is null, not exporting");
				continue;
			}
			if (gameAsset.isBroken()) {
				System.out.println("Game asset is broken, not exporting");
				continue;
			}

			gameAssetsToExport.add(gameAsset);
			gameAssetsToExport.addAll(gameAsset.dependentGameAssets); //Add any dependnet game assets
		}

		GameAssetsExportStructure gameAssetExportStructure = new GameAssetsExportStructure();

		for (GameAsset<?> gameAsset : gameAssetsToExport) {
			GameAssetExportStructure assetExportStructure = new GameAssetExportStructure();
			assetExportStructure.identifier = gameAsset.nameIdentifier;
			assetExportStructure.type = gameAsset.type;
			for (RawAsset dependentRawAsset : gameAsset.dependentRawAssets) {
				assetExportStructure.absolutePathsOfRawFiles.add(dependentRawAsset.handle.path());
			}
			gameAssetExportStructure.gameAssets.add(assetExportStructure);
		}

		FileHandle assetRepoExportFile = Gdx.files.absolute(SceneEditorWorkspace.getInstance().getProjectPath()).child("assetExport.json");
		assetRepoExportFile.writeString(json.toJson(gameAssetExportStructure), false);


	}

	private void findAllPrefabs (FileHandle assets, ObjectSet<TypeIdentifierPair> identifiersBeingUsedByComponents) {
		FileHandle[] list = assets.list();
		for (FileHandle handle : list) {
			if (handle.isDirectory()) {
				findAllPrefabs(handle, identifiersBeingUsedByComponents);
			} else {
				if (handle.extension().equals("prefab")) {
					//Get the raw asset
					GameAsset<Prefab> gameAsset = dataMaps.fileHandleGameAssetObjectMap.get(handle);
					if (gameAsset != null) {
						identifiersBeingUsedByComponents.add(new TypeIdentifierPair(GameAssetType.PREFAB, gameAsset.nameIdentifier));

						JsonValue prefab = new JsonReader().parse(gameAsset.getRootRawAsset().handle);
						JsonValue gameObject = prefab.get("root");

						//Get all dependent assets of this prefab
						collectGameObjectExportedAssets(identifiersBeingUsedByComponents, gameObject);
					}
				}
			}
		}
	}

	private GameAssetType getGameAssetTypeFromClazz (String componentClazz) {
		try {
			Class<? extends GameResourceOwner> aClass = ClassReflection.forName(componentClazz);
			return GameAssetType.getAssetTypeFromGameResourceOwner(aClass);
		} catch (ReflectionException e) {
			throw new RuntimeException(e);
		}
	}

	public static class GameAssetExportStructure {
		String identifier;
		GameAssetType type;
		Array<String> absolutePathsOfRawFiles = new Array<>();
	}

	public static class GameAssetsExportStructure {
		Array<GameAssetExportStructure> gameAssets = new Array<>();
	}

	private boolean componentIsResourceOwner (String componentClazz) {
		try {
			Class aClass = ClassReflection.forName(componentClazz);

			return GameResourceOwner.class.isAssignableFrom(aClass);
		} catch (ReflectionException e) {
			e.printStackTrace();
			return false;
		}
	}

	private String getGameAssetIdentifierFromRawAsset (RawAsset asset) {
		return asset.handle.nameWithoutExtension();
	}

	private void createGameAsset (FileHandle key, RawAsset value) {
		String gameAssetIdentifier = getGameAssetIdentifierFromRawAsset(value);

		GameAssetType assetTypeFromExtension = null;
		try {
			assetTypeFromExtension = GameAssetType.getAssetTypeFromExtension(key.extension());
		} catch (GameAssetType.NoAssetTypeException e) {
			throw new RuntimeException(e);
		}

		boolean shouldUpdate = false;
		if (getAssetForIdentifier(gameAssetIdentifier, assetTypeFromExtension) != null) {
			shouldUpdate = true;
		}

		GameAsset gameAsset = createGameAssetForType(assetTypeFromExtension, gameAssetIdentifier, value, true);


		if (shouldUpdate) {
			GameAsset<?> oldGameAsset = getAssetForIdentifier(gameAssetIdentifier, assetTypeFromExtension);
			for (RawAsset dependentRawAsset : oldGameAsset.dependentRawAssets) {
				dependentRawAsset.gameAssetReferences.removeValue(oldGameAsset, true);
			}
			Array<GameAsset.GameAssetUpdateListener> listeners = oldGameAsset.listeners;
			gameAsset.listeners.addAll(listeners);
			oldGameAsset.listeners.clear();

			//Find all components in the current scene that was using this and reset their game asset to the new one
			Array<GameObject> list = new Array<>();
			SceneEditorWorkspace sceneEditorWorkspace = SceneEditorWorkspace.getInstance();
			if (sceneEditorWorkspace != null && sceneEditorWorkspace.getRootGO() != null) {
				sceneEditorWorkspace.getRootGO().getChildrenByComponent(GameResourceOwner.class, list);
				for (GameObject gameObject : list) {
					for (AComponent component : gameObject.getComponents()) {
						if (component instanceof GameResourceOwner) {
							GameResourceOwner<?> gameResourceOwner = (GameResourceOwner<?>)component;

							GameAsset<?> gameResource = gameResourceOwner.getGameResource();
							if (gameResource.type == GameAssetType.SKELETON) {
								System.out.println();
							}

							if (gameResourceOwner.getGameResource().equals(oldGameAsset)) {
								gameResourceOwner.setGameAsset(gameAsset);
							}
						}
					}
				}
			}
		}

		if (shouldUpdate) {
			gameAsset.setUpdated();
		}


		if (gameAsset == null) return;
		System.out.println("Registering game asset " + gameAssetIdentifier + " " + gameAsset + " " + value.handle.path() + " " + assetTypeFromExtension);

		putAssetForIdentifier(gameAssetIdentifier, assetTypeFromExtension, gameAsset);
		dataMaps.putFileHandleGameAsset(key, gameAsset);
	}

	public FileHandle copySampleParticleToProject (FileHandle preferredDestination) {
		FileHandle originalTls = Gdx.files.internal("addons/scene/missing/sample.tls");
		FileHandle imageRequired = Gdx.files.internal("addons/scene/missing/white.png");

		if (!preferredDestination.child(imageRequired.name()).exists()) {
			AssetRepository.getInstance().copyRawAsset(imageRequired, preferredDestination);
		} else {
			System.out.println("Ignoring copying white, since we have it already");
		}

		return AssetRepository.getInstance().copyRawAsset(originalTls, preferredDestination);
	}

	public GameAsset<?> createGameAssetForType (GameAssetType assetTypeFromExtension, String gameAssetIdentifier, RawAsset value, boolean createLinks) {
		if (!assetTypeFromExtension.isRootGameAsset()) {
			throw new GdxRuntimeException("Trying to load a game asset from a non root asset");
		}

		GameAsset<?> gameAssetOut = null;


		try {
			switch (assetTypeFromExtension) {
			case SPRITE:
				GameAsset<Texture> textureGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = textureGameAsset;


				if (createLinks) {
					value.gameAssetReferences.add(textureGameAsset);
					textureGameAsset.dependentRawAssets.add(value);
				}

				textureGameAsset.setResourcePayload(new Texture(value.handle));


				break;
			case ATLAS:
				GameAsset<TextureAtlas> textureAtlasGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = textureAtlasGameAsset;

				TextureAtlas.TextureAtlasData textureAtlasData = new TextureAtlas.TextureAtlasData(value.handle, value.handle.parent(), false);
				TextureAtlas atlas = new TextureAtlas(textureAtlasData);
				textureAtlasGameAsset.setResourcePayload(atlas);

				if (createLinks) {
					value.gameAssetReferences.add(textureAtlasGameAsset);
					textureAtlasGameAsset.dependentRawAssets.add(value);

					for (TextureAtlas.TextureAtlasData.Page page : textureAtlasData.getPages()) {
						FileHandle textureFile = page.textureFile;
						if (!dataMaps.fileHandleRawAssetMap.containsKey(textureFile)) {
							throw new GdxRuntimeException("Corruption, texture file does not exist" + textureFile);
						}

						RawAsset rawAssetForPage = dataMaps.fileHandleRawAssetMap.get(textureFile);

						rawAssetForPage.gameAssetReferences.add(textureAtlasGameAsset);
						textureAtlasGameAsset.dependentRawAssets.add(rawAssetForPage);
					}
				}



				break;

			case SKELETON:

				GameAsset<SkeletonData> skeletonDataGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = skeletonDataGameAsset;

				//Gotta try load the atlas
				String skeleName = value.handle.nameWithoutExtension();
				FileHandle atlasFile = value.handle.parent().child(skeleName + ".atlas");
				TextureAtlas.TextureAtlasData skeleAtlasData = new TextureAtlas.TextureAtlasData(atlasFile, atlasFile.parent(), false);
				TextureAtlas skeleAtlas = new TextureAtlas(skeleAtlasData);

				if (atlasFile.exists()) {
					SkeletonBinary skeletonBinary = new SkeletonBinary(skeleAtlas);

					SpineMetadata metaData = (SpineMetadata)value.metaData;

					skeletonBinary.setScale(1f/metaData.pixelsPerUnit);

					SkeletonData skeletonData = skeletonBinary.readSkeletonData(value.handle);
					skeletonDataGameAsset.setResourcePayload(skeletonData);

					if (createLinks) {
						value.gameAssetReferences.add(skeletonDataGameAsset);
						skeletonDataGameAsset.dependentRawAssets.add(value);

						RawAsset skeleAtlasRawAsset = dataMaps.fileHandleRawAssetMap.get(atlasFile);
						skeletonDataGameAsset.dependentRawAssets.add(skeleAtlasRawAsset);

						for (TextureAtlas.TextureAtlasData.Page page : skeleAtlasData.getPages()) {
							FileHandle textureFile = page.textureFile;
							if (!dataMaps.fileHandleRawAssetMap.containsKey(textureFile)) {
								throw new GdxRuntimeException("Corruption, texture file does not exist" + textureFile);
							}

							RawAsset rawAssetForPage = dataMaps.fileHandleRawAssetMap.get(textureFile);
							rawAssetForPage.gameAssetReferences.add(skeletonDataGameAsset);
							skeletonDataGameAsset.dependentRawAssets.add(rawAssetForPage);
						}
					}

				}

				break;
			case SOUND:
				break;
			case VFX:

				GameAsset<ParticleEffectDescriptor> particleEffectDescriptorGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);

				gameAssetOut = particleEffectDescriptorGameAsset;

				if (createLinks) {
					particleEffectDescriptorGameAsset.dependentRawAssets.add(value);
				}

				ParticleEffectDescriptor particleEffectDescriptor = new ParticleEffectDescriptor();
				particleEffectDescriptor.setAssetProvider(new AssetProvider() {
					@Override
					public <T> T findAsset (String assetName, Class<T> clazz) {

						if (Sprite.class.isAssignableFrom(clazz)) {
							GameAsset<Texture> gameAsset = getAssetForIdentifier(assetName, GameAssetType.SPRITE);

							if (gameAsset != null) {
								if (createLinks) {
									for (RawAsset dependentRawAsset : gameAsset.dependentRawAssets) {
										particleEffectDescriptorGameAsset.dependentRawAssets.add(dependentRawAsset);
									}
								}
								return (T)new Sprite(gameAsset.getResource());
							} else {
								particleEffectDescriptorGameAsset.setBroken(new Exception("Cannot find " + assetName));
							}
						}

						throw new GdxRuntimeException("Couldn't find asset " + assetName + " for type " + clazz);
					}
				});

				//We need to find the asset that is linked to
				//P should be same directory, lets find it

				FileHandle pFile = value.handle.parent().child(value.handle.nameWithoutExtension() + ".p");
				if (!pFile.exists()) {
					throw new GdxRuntimeException("No p file for tls " + value.handle.path() + " " + pFile.path());
				}

				RawAsset rawAssetPFile = dataMaps.fileHandleRawAssetMap.get(pFile);

				try {
					particleEffectDescriptor.load(rawAssetPFile.handle);
				} catch (Exception e) {
					System.out.println("Failure to load particle effect");
					throw e;
				}

				particleEffectDescriptorGameAsset.setResourcePayload(particleEffectDescriptor);

				if (createLinks) {
					value.gameAssetReferences.add(particleEffectDescriptorGameAsset);
					particleEffectDescriptorGameAsset.dependentRawAssets.add(rawAssetPFile);
				}

				break;
			case VFX_OUTPUT:
				break;
			case SCRIPT:

				GameAsset<String> scriptGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = scriptGameAsset;

				scriptGameAsset.setResourcePayload("ScriptDummy");

				if (createLinks) {
					value.gameAssetReferences.add(scriptGameAsset);
					scriptGameAsset.dependentRawAssets.add(value);
				}

				break;
			case TWEEN:
				GameAsset<String> tweenGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = tweenGameAsset;
				tweenGameAsset.setResourcePayload("Dummy");

				if (createLinks) {
					value.gameAssetReferences.add(tweenGameAsset);
					tweenGameAsset.dependentRawAssets.add(value);
				}

				break;
			case PREFAB:
				GameAsset<Prefab> prefabGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = prefabGameAsset;

				Prefab prefab = Prefab.from(value.handle);

				prefabGameAsset.setResourcePayload(prefab);

				if (createLinks) {
					value.gameAssetReferences.add(prefabGameAsset);
					prefabGameAsset.dependentRawAssets.add(value);
				}

				break;
			case SCENE:
				GameAsset<Scene> sceneGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = sceneGameAsset;

				Scene scene = new Scene();
				scene.path = value.handle.path();
				scene.loadFromPath();

				sceneGameAsset.setResourcePayload(scene);

				if (createLinks) {
					value.gameAssetReferences.add(sceneGameAsset);
					sceneGameAsset.dependentRawAssets.add(value);
				}

				break;
			case TILE_PALETTE:
				GameAsset<TilePaletteData> paletteGameAsset = new GameAsset<>(gameAssetIdentifier, assetTypeFromExtension);
				gameAssetOut = paletteGameAsset;

				TilePaletteData paletteData = json.fromJson(TilePaletteData.class, value.handle);

				paletteGameAsset.setResourcePayload(paletteData);

				if (createLinks) {
					value.gameAssetReferences.add(paletteGameAsset);
					paletteGameAsset.dependentRawAssets.add(value);

					for (ObjectMap.Entry<UUID, GameAsset<?>> reference : paletteData.references) {
						//Add the dependent game asset's root assets
						paletteGameAsset.dependentGameAssets.addAll(reference.value);
					}
				}

				break;
			case DIRECTORY:
				break;
			}
		} catch (Exception e) {
			if (gameAssetOut != null) {
				e.printStackTrace();
				gameAssetOut.setBroken(e);
				System.out.println("Marking asset as broken " + gameAssetOut + " " + value.handle.path());
			}
		}

		return gameAssetOut;
	}

	public void saveGameAssetResourceJsonToFile (GameAsset<?> gameAsset) {
		RawAsset rootRawAsset = gameAsset.getRootRawAsset();

		String jsonString = json.toJson(gameAsset.getResource());
		rootRawAsset.handle.writeString(jsonString, false);
	}

	public GameObject copyGameObject (GameObject gameObject) {
		String serialized = json.toJson(gameObject);
		GameObject newObject = json.fromJson(GameObject.class, serialized);
		newObject.uuid = UUID.randomUUID();
		return newObject;
	}

	private void collectRawResourceFromDirectory (FileHandle dir, boolean checkGameResources) {

		if (!dir.isDirectory()) {
			rawAssetCreated(dir, checkGameResources);
		}

		FileHandle[] list = dir.list();
		for (FileHandle fileHandle : list) {

			if (fileHandle.isDirectory()) {
				collectRawResourceFromDirectory(fileHandle, checkGameResources);
			} else if (shouldIgnoreAsset(fileHandle)) {
				continue;
			} else if (!dataMaps.fileHandleRawAssetMap.containsKey(fileHandle)) {
				rawAssetCreated(fileHandle, checkGameResources);
			}
		}
	}

	private boolean shouldIgnoreAsset (FileHandle fileHandle) {
		String extension = fileHandle.extension();

		if (fileHandle.name().equals(".DS_Store")) return true;
		if (extension.equals("meta")) return true;

		return false;
	}

	public void registerFileWatching () {

	}

	public void updateVFXTLS (RawAsset tlsRawAsset, boolean checkGameResources) {
		ExportData exportData = exportTLSDataToP(tlsRawAsset.handle);
		String exportDataJson = writeTalosPExport(exportData);

		FileHandle exportedPFile = tlsRawAsset.handle.parent().child(tlsRawAsset.handle.nameWithoutExtension() + ".p");
		exportedPFile.writeString(exportDataJson, false);

		rawAssetCreated(exportedPFile, checkGameResources);
	}

	public void rawAssetCreated (FileHandle fileHandle, boolean checkGameResources) {
		try {
			GameAssetType assetTypeFromExtension = GameAssetType.getAssetTypeFromExtension(fileHandle.extension());

			RawAsset rawAsset = new RawAsset(fileHandle);

			if (assetTypeFromExtension == GameAssetType.VFX) {
				updateVFXTLS(rawAsset, checkGameResources);
			}

			FileHandle metadataHandleFor = AssetImporter.getMetadataHandleFor(fileHandle);
			if (metadataHandleFor.exists()) {
				try {
					Class<? extends AMetadata> metaClassForType = GameAssetType.getMetaClassForType(assetTypeFromExtension);
					rawAsset.metaData = json.fromJson(metaClassForType, metadataHandleFor);
					rawAsset.metaData.setLinkRawAsset(rawAsset);
				} catch (Exception e) {
					e.printStackTrace();

					System.out.println("Error reading meta for " + metadataHandleFor.path() + " " + metadataHandleFor.readString());
					rawAsset.metaData = createMetaDataForAsset(rawAsset);
				}
			} else {
				rawAsset.metaData = createMetaDataForAsset(rawAsset);
			}

			dataMaps.putUUIRawAsset(rawAsset.metaData.uuid, rawAsset);
			dataMaps.putFileHandleRawAsset(fileHandle, rawAsset);

			System.out.println("Raw asset created" + rawAsset.handle.path());

			if (checkGameResources) {
				checkAllGameAssetCreation();
			}
		} catch (GameAssetType.NoAssetTypeException noAssetTypeException) {
			//Its not an asset
		}
	}

	@EventHandler
	public void onScriptFileChanged (ScriptFileChangedEvent event) {
		FileHandle realScriptHandle = event.file;
		String proxyMetaPath = realScriptHandle.path() + ".meta";
		FileHandle proxyMetaFileHandle = new FileHandle(proxyMetaPath);
		if (!proxyMetaFileHandle.exists()) {
			return;
		}

		Class<? extends AMetadata> metaClassForType = GameAssetType.getMetaClassForType(GameAssetType.SCRIPT);
		ScriptMetadata metadata = (ScriptMetadata) json.fromJson(metaClassForType, proxyMetaFileHandle);

		RawAsset rawAsset = dataMaps.uuidRawAssetMap.get(metadata.uuid);
		FileHandle proxyScriptHandle = dataMaps.fileHandleRawAssetMap.findKey(rawAsset, true);
		if (rawAsset == null) {
			// CASE: someone put file handle externally in project folder
			return;
		}
		realScriptHandle.copyTo(proxyScriptHandle);

		AMetadata metaData = rawAsset.metaData;
		if (metaData instanceof ScriptMetadata) {
			metaData.postProcessForHandle(realScriptHandle);

			//Save the meta data
			FileHandle metadataHandleFor = AssetImporter.getMetadataHandleFor(proxyScriptHandle);
			metadataHandleFor.writeString(json.prettyPrint(metaData), false);

			GameObject rootGO = SceneEditorWorkspace.getInstance().getRootGO();
			Array<ScriptComponent> updatedComponents = new Array<>();
			updateScriptComponentsForNewMeta(rootGO, (ScriptMetadata) metaData, updatedComponents);

			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run () {
					for (ScriptComponent updatedComponent : updatedComponents) {
						Notifications.fireEvent(Notifications.obtainEvent(ComponentUpdated.class).set(updatedComponent, false));
					}
				}
			});
		}
	}

	private void updateScriptComponentsForNewMeta (GameObject gameObject, ScriptMetadata metaData, Array<ScriptComponent> updatedComponents) {
		if (gameObject.hasComponent(ScriptComponent.class)) {
			ScriptComponent component = gameObject.getComponent(ScriptComponent.class);
			AMetadata componentMeta = component.getGameResource().getRootRawAsset().metaData;
			if (componentMeta.uuid == metaData.uuid) {
				component.importScriptPropertiesFromMeta(true);
				updatedComponents.add(component);
			}
		}

		Array<GameObject> children = gameObject.getGameObjects();
		if (children != null) {
			for (int i = 0; i < children.size; i++) {
				GameObject child = children.get(i);
				updateScriptComponentsForNewMeta(child, metaData, updatedComponents);
			}
		}
	}

	private <T extends AMetadata> T createMetaDataForAsset (RawAsset rawAsset) {
		if (rawAsset.handle.isDirectory()) {
			return (T)new DirectoryMetadata();
		} else {

			FileHandle rawAssetHandle = rawAsset.handle;
			String extension = rawAssetHandle.extension();

			GameAssetType assetTypeFromExtension = null;
			try {
				assetTypeFromExtension = GameAssetType.getAssetTypeFromExtension(extension);
			} catch (GameAssetType.NoAssetTypeException e) {
				throw new RuntimeException(e);
			}
			T metaForType = (T)GameAssetType.createMetaForType(assetTypeFromExtension);

			if (metaForType instanceof ScriptMetadata) {
				metaForType.postProcessForHandle(rawAssetHandle);
			}

			//Save the meta data
			FileHandle metadataHandleFor = AssetImporter.getMetadataHandleFor(rawAssetHandle);
			metadataHandleFor.writeString(json.prettyPrint(metaForType), false);

			metaForType.setLinkRawAsset(rawAsset);

			return metaForType;
		}
	}

	public static FileHandle getExportedScriptsFolderHandle () {
		String projectPath = SceneEditorWorkspace.getInstance().getProjectPath();
		return Gdx.files.absolute(projectPath).parent().child("src").child("scene").child("scripts");
	}

	public GameAsset<?> getAssetForPath (FileHandle file, boolean ignoreBroken) {
		if (dataMaps.fileHandleGameAssetObjectMap.containsKey(file)) {
			GameAsset<?> gameAsset = dataMaps.fileHandleGameAssetObjectMap.get(file);
			if (ignoreBroken) {
				return gameAsset;
			} else {
				if (!gameAsset.isBroken()) {
					return gameAsset;
				} else {
					return null;
				}
			}
		}
		return null;
	}

	private void deleteFileImpl (FileHandle handle) {
		FileHandle metadataHandleFor = AssetImporter.getMetadataHandleFor(handle);
		if (metadataHandleFor.exists()) {
			metadataHandleFor.delete();
		}
		handle.delete();
		Array<GameAsset> gameAssetsToUpdate = new Array<>();

		if (dataMaps.fileHandleRawAssetMap.containsKey(handle)) {
			RawAsset rawAsset = dataMaps.fileHandleRawAssetMap.get(handle);
			gameAssetsToUpdate.addAll(rawAsset.gameAssetReferences);


			for (GameAsset gameAsset : gameAssetsToUpdate) {
				gameAsset.dependentRawAssets.removeValue(rawAsset, true);

				//check if this is a broken game asset, or one to be removed
				if (gameAsset.dependentRawAssets.size > 0) {
					gameAsset.setBroken(new Exception("Removed one of the dependent raw files"));
					gameAsset.setUpdated();
				} else {
					gameAsset.setBroken(new Exception("Game Asset Removed"));
					gameAsset.setUpdated();
					dataMaps.removeFileHandleGameAssetObjectMap(rawAsset.handle);
					identifierGameAssetMap.get(gameAsset.type).remove(gameAsset.nameIdentifier);
				}

			}

			dataMaps.removeFileHandleRawAsset(handle);
			dataMaps.removeUUIDRawAsset(rawAsset.metaData.uuid);
		}
	}
	public void deleteRawAsset (FileHandle handle) {
		if (handle.isDirectory()) {
			for (FileHandle fileHandle : handle.list()) {
				deleteRawAsset(fileHandle);
			}
		}
		deleteFileImpl(handle);

	}

	public FileHandle copyRawAsset (FileHandle file, FileHandle directory) {
		String fileName = file.name();
		if (directory.child(fileName).exists()) {
			String baseName = file.nameWithoutExtension();

			fileName = NamingUtils.getNewName(baseName, new Supplier<Collection<String>>() {
				@Override
				public Collection<String> get () {
					ArrayList<String> fileNames = new ArrayList<>();
					for (FileHandle fileHandle : directory.list()) {
						fileNames.add(fileHandle.nameWithoutExtension());
					}
					return fileNames;
				}
			}) + "." + file.extension();

		}
		// do not allow stupid characters
		Pattern pattern = Pattern.compile("[/?<>\\\\:*|\"]");
		Matcher matcher = pattern.matcher(fileName);
		fileName = matcher.replaceAll("_");
		FileHandle dest = directory.child(fileName);
		if (file.isDirectory()) { // recursively copy directory and its contents
			FileHandle[] list = file.list();
			//Change the destination and copy all its children into the new destination
			dest.mkdirs();
			for (FileHandle fileHandle : list) {
				if (fileHandle.extension().equals("meta")) continue; //Don't copy meta

				copyRawAsset(fileHandle, dest);
			}
		} else { // single file
			file.copyTo(dest);
		}

		collectRawResourceFromDirectory(dest, true);

		return dest;
	}


	static class MovingDirNode {
		FileHandle oldHandle;
		FileHandle newHandle;

		Array<MovingDirNode> children = new Array<>();
	}


	private void updateChildReferences (MovingDirNode parent) {
		for (MovingDirNode child : parent.children) {

			FileHandle oldHandle = child.oldHandle;

			FileHandle newHandle = parent.newHandle.child(oldHandle.name());
			child.newHandle = newHandle;


			if (!newHandle.isDirectory()) {
				RawAsset rawAsset = dataMaps.removeFileHandleRawAsset(oldHandle);

				if (rawAsset == null) {
					System.out.println();
				}
				rawAsset.handle = newHandle;

				dataMaps.putFileHandleRawAsset(newHandle, rawAsset);

				AssetPathChanged assetPathChanged = Notifications.obtainEvent(AssetPathChanged.class);
				assetPathChanged.oldRelativePath = relative(oldHandle);
				assetPathChanged.newRelativePath = relative(newHandle);
				Notifications.fireEvent(assetPathChanged);

				for (GameAsset gameAssetReference : rawAsset.gameAssetReferences) {
					gameAssetReference.setUpdated();
				}

				if (isRootGameResource(rawAsset)) {
					GameAsset gameAsset = dataMaps.removeFileHandleGameAssetObjectMap(oldHandle);
					dataMaps.putFileHandleGameAsset(newHandle, gameAsset);

					gameAsset.setUpdated();
				}
			}
			updateChildReferences(child);

		}
	}
	private void populateChildren (FileHandle fileHandle, MovingDirNode fileNode) {
		FileHandle[] children = fileHandle.list();
		for (FileHandle handle : children) {
			if (handle.extension().equals("meta")) continue;

			MovingDirNode value = new MovingDirNode();

			value.oldHandle = handle;
			fileNode.children.add(value);

			if (handle.isDirectory()) {
				populateChildren(handle, value);
			}
		}
	}

	//Could be a rename or a move
	public void moveFile (FileHandle file, FileHandle destination, boolean rename) {
		AssetImporter.moveFile(file, destination, true, rename);
	}
	public void moveFile (FileHandle file, FileHandle destination, boolean checkGameAssets, boolean rename) {


		if (file.isDirectory()) {
			//Moving a folder
			if (destination.exists() && !destination.isDirectory()) {
				throw new GdxRuntimeException("Trying to move a directory to a file");
			}

			if (!rename && !destination.name().equals(file.name())) {
				//Make sure to preserve the file structure when copying folders
				destination = destination.child(file.name());
			}

			MovingDirNode rootNode = new MovingDirNode();
			rootNode.oldHandle = file;
			rootNode.newHandle = destination;
			populateChildren(file, rootNode);

			file.moveTo(destination);

			updateChildReferences(rootNode);

		} else {
			//Moving a file
			if (destination.isDirectory()) {
				//Moving a file into a directory

				RawAsset rawAsset = dataMaps.removeFileHandleRawAsset(file);
				dataMaps.removeFileHandleGameAssetObjectMap(file);

				FileHandle oldMeta = AssetImporter.getMetadataHandleFor(file);

				if (oldMeta.exists()) {
					oldMeta.moveTo(destination);
				}

				file.moveTo(destination);

				if (rawAsset == null) {
					//It wasn't something we were tracking, must be broken so we just ignore it

					return;
				}

				//Lets check to see if its a tls for special case
				if (file.extension().equals("tls")) {
					//We need to move the .p too
					FileHandle pFile = file.parent().child(file.nameWithoutExtension() + ".p");
					if (pFile.exists()) {
						//Copy this too,
						AssetImporter.moveFile(pFile, destination, false, false);
					}
				}


				FileHandle newHandle = destination.child(file.name());

				AssetPathChanged assetPathChanged = Notifications.obtainEvent(AssetPathChanged.class);
				assetPathChanged.oldRelativePath = relative(file);
				assetPathChanged.newRelativePath = relative(newHandle);
				Notifications.fireEvent(assetPathChanged);

				dataMaps.putFileHandleRawAsset(newHandle, rawAsset);

				rawAsset.handle = newHandle;

				for (GameAsset gameAssetReference : rawAsset.gameAssetReferences) {
					gameAssetReference.setUpdated();
				}

			} else {
				//Its a rename
				if (dataMaps.fileHandleRawAssetMap.containsKey(file)) {
					RawAsset rawAsset = dataMaps.fileHandleRawAssetMap.get(file);
					dataMaps.removeFileHandleRawAsset(file);

					FileHandle oldMeta = AssetImporter.getMetadataHandleFor(file);

					oldMeta.moveTo(destination.parent().child(destination.name() + ".meta"));
					file.moveTo(destination);

					//Lets check to see if its a tls for special case
					if (file.extension().equals("tls")) {
						//We need to move the .p too
						FileHandle pFile = file.parent().child(file.nameWithoutExtension() + ".p");
						if (pFile.exists()) {
							//Copy this too,
							AssetImporter.moveFile(pFile, destination, false);
						}
					}


					AssetPathChanged assetPathChanged = Notifications.obtainEvent(AssetPathChanged.class);
					assetPathChanged.oldRelativePath = relative(file);
					assetPathChanged.newRelativePath = relative(destination);
					Notifications.fireEvent(assetPathChanged);

					if (isRootGameResource(rawAsset)) {
						//We need to update the game assets identifier
						String gameAssetIdentifierFromRawAsset = getGameAssetIdentifierFromRawAsset(rawAsset);
						GameAssetType typeFromExtension = null;
						try {
							typeFromExtension = GameAssetType.getAssetTypeFromExtension(rawAsset.handle.extension());
						} catch (GameAssetType.NoAssetTypeException e) {
							throw new RuntimeException(e);
						}

						GameAsset<?> assetForIdentifier = getAssetForIdentifier(gameAssetIdentifierFromRawAsset, typeFromExtension);

						if (assetForIdentifier != null) {
							GameAsset<?> removedGameAsset = identifierGameAssetMap.get(assetForIdentifier.type).remove(gameAssetIdentifierFromRawAsset);
							putAssetForIdentifier(destination.nameWithoutExtension(), removedGameAsset.type, removedGameAsset);

							removedGameAsset.setUpdated();
						} else {
							System.err.println("No game asset found for identifier " + gameAssetIdentifierFromRawAsset);
						}
					}

					dataMaps.putFileHandleRawAsset(destination, rawAsset);

					rawAsset.handle = destination;

					for (GameAsset gameAssetReference : rawAsset.gameAssetReferences) {
						gameAssetReference.setUpdated();
					}

				} else {
					//Just move it
					file.moveTo(destination);

					collectRawResourceFromDirectory(file, true);
				}
			}

		}

		if (checkGameAssets) {
			checkAllGameAssetCreation();
		}
	}


	private boolean isRootGameResource (RawAsset rawAsset) {
		GameAssetType assetTypeFromExtension = null;
		try {
			assetTypeFromExtension = GameAssetType.getAssetTypeFromExtension(rawAsset.handle.extension());
		} catch (GameAssetType.NoAssetTypeException e) {
			throw new RuntimeException(e);
		}
		return assetTypeFromExtension.isRootGameAsset();
	}

	private static class AssetNameFieldFilter implements TextField.TextFieldFilter {

		@Override
		public boolean acceptChar(TextField textField, char c) {
			return !(c == '/'
					|| c == '?'
					|| c == '<'
					|| c == '>'
					|| c == '\\'
					|| c == ':'
					|| c == '*'
					|| c == '|'
					|| c == '"');
		}
	}

}
