package com.talosvfx.talos.editor.addons.scene.utils.importers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.talosvfx.talos.TalosMain;
import com.talosvfx.talos.editor.addons.scene.SceneEditorAddon;
import com.talosvfx.talos.editor.addons.scene.SceneEditorWorkspace;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import com.talosvfx.talos.editor.addons.scene.logic.Prefab;
import com.talosvfx.talos.editor.addons.scene.utils.AMetadata;
import com.talosvfx.talos.editor.addons.scene.utils.metadata.SpriteMetadata;
import com.talosvfx.talos.editor.addons.scene.utils.metadata.TlsMetadata;
import com.talosvfx.talos.editor.project.FileTracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class AssetImporter {

    public FileTracker.Tracker assetTracker;

    public enum AssetType {
        SPRITE,
        TLS,
        SPINE,
        ATLAS
    }

    private static ObjectMap<AssetType, AbstractImporter> importerMap = new ObjectMap();

    public AssetImporter() {
        assetTracker = handle -> assetUpdated(handle);

        importerMap.put(AssetType.SPRITE, new SpriteImporter());
        importerMap.put(AssetType.TLS, new TlsImporter());
        importerMap.put(AssetType.SPINE, new SpineImporter());
        importerMap.put(AssetType.ATLAS, new AtlasImporter());
    }

    public static FileHandle attemptToImport (FileHandle handle) {
        FileHandle importedAsset = null;
        AbstractImporter importer = null;
        if(handle.extension().equals("png")) {
            importer = importerMap.get(AssetType.SPRITE);
        } else if(handle.extension().equals("tls")) {
            importer = importerMap.get(AssetType.TLS);
        } else if(handle.extension().equals("skel")) {
            importer = importerMap.get(AssetType.SPINE);
        } else if(handle.extension().equals("atlas")) {
            importer = importerMap.get(AssetType.ATLAS);
        }

        importedAsset = importer.importAsset(handle);

        if(importedAsset != null) {
            String projectPath = SceneEditorAddon.get().workspace.getProjectPath();
            SceneEditorAddon.get().projectExplorer.loadDirectoryTree(projectPath);
            SceneEditorAddon.get().projectExplorer.expand(importedAsset.path());

            TalosMain.Instance().ProjectController().saveProject();
        }

        return importedAsset;
    }

    private void assetUpdated (FileHandle handle) {
        SceneEditorAddon.get().workspace.updateAsset(handle); //todo: maybe instead worth using events
    }

    // Check up on things, tidy up a bit
    public void housekeep (String projectPath) {
        FileHandle project = Gdx.files.absolute(projectPath);
        Array<FileHandle> tlsFiles = new Array<>();
        findInPath(project, "tls", tlsFiles);

        for(FileHandle tlsHandle: tlsFiles) {
            String checksum = checkSum(tlsHandle);
            TlsMetadata tlsMetadata = readMetadataFor(tlsHandle, TlsMetadata.class);
            if(!tlsMetadata.tlsChecksum.equals(checksum)) {
                ((TlsImporter)importerMap.get(AssetType.TLS)).exportTlsFile(tlsHandle);

                tlsMetadata.tlsChecksum = checksum;
                FileHandle metadataHandle = AssetImporter.getMetadataHandleFor(tlsHandle);
                AssetImporter.saveMetadata(metadataHandle, tlsMetadata);
            }
        }
    }

    public static <T extends AMetadata> T readMetadataFor (FileHandle assetHandle, Class<? extends T> clazz) {
        FileHandle handle = getMetadataHandleFor(assetHandle);
        String data = handle.readString();
        Json json = new Json();
        T object = json.fromJson(clazz, data);
        return object;
    }

    public static <T extends AMetadata> T readMetadata (FileHandle handle, Class<? extends T> clazz) {
        String data = handle.readString();
        Json json = new Json();
        T object = json.fromJson(clazz, data);
        return object;
    }

    public static FileHandle getMetadataHandleFor (FileHandle handle) {
        FileHandle metadataHandle = Gdx.files.absolute(handle.parent().path() + File.separator + handle.nameWithoutExtension() + ".meta");
        return metadataHandle;
    }

    public static FileHandle getMetadataHandleFor (String assetPath) {
        FileHandle handle = Gdx.files.absolute(assetPath);
        FileHandle metadataHandle = Gdx.files.absolute(handle.parent().path() + File.separator + handle.nameWithoutExtension() + ".meta");
        return metadataHandle;
    }

    public static void createAssetInstance(FileHandle fileHandle, GameObject parent) {
        if(fileHandle.extension().equals("png")) {
            // check if non imported nine patch
            if(fileHandle.name().endsWith(".9.png")) {
                // import it
                attemptToImport(fileHandle);
            } else {
                importerMap.get(AssetType.SPRITE).makeInstance(fileHandle, parent);
            }
        }

        if(fileHandle.extension().equals("tls")) {
            importerMap.get(AssetType.TLS).makeInstance(fileHandle, parent);
        }
        if(fileHandle.extension().equals("p")) {
            importerMap.get(AssetType.TLS).makeInstance(fileHandle, parent);
        }

        if(fileHandle.extension().equals("skel")) {
            importerMap.get(AssetType.SPINE).makeInstance(fileHandle, parent);
        }

        if(fileHandle.extension().equals("prefab")) {
            SceneEditorWorkspace workspace = SceneEditorAddon.get().workspace;
            Vector2 sceneCords = workspace.getMouseCordsOnScene();
            Prefab prefab = Prefab.from(fileHandle);
            GameObject gameObject = workspace.createFromPrefab(prefab, sceneCords, parent);
        }
    }


    public static void findInPath(FileHandle path, String extensionFilter, Array<FileHandle> result) {
        if (path.isDirectory()) {
            FileHandle[] list = path.list();
            for(int i = 0; i < list.length; i++) {
                FileHandle item = list[i];
                findInPath(item, extensionFilter, result);
            }
        } else {
            if(path.extension().equals(extensionFilter)) {
                result.add(path);
            }
        }
    }

    public static String checkSum(FileHandle fileHandle) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            // DigestInputStream is better, but you also can hash file like this.
            try (InputStream fis = new FileInputStream(fileHandle.path())) {
                byte[] buffer = new byte[1024];
                int nread;
                while ((nread = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, nread);
                }
            }

            // bytes to hex
            StringBuilder result = new StringBuilder();
            for (byte b : md.digest()) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (Exception e) {

        }

        return "";
    }

    public static void saveMetadata (FileHandle handle, AMetadata aMetadata) {
        Json json = new Json();
        String data = json.toJson(aMetadata);
        handle.writeString(data, false);
    }

    public static FileHandle makeSimilar (FileHandle fileHandle, String extension) {
        String path = fileHandle.parent().path() + File.separator + fileHandle.nameWithoutExtension() + "." + extension;
        return Gdx.files.absolute(path);
    }

    public static FileHandle suggestNewName (String path, String filename, String extension) {
        FileHandle handle = Gdx.files.absolute(path);

        if(handle.exists() && handle.isDirectory()) {
            String name = filename + "." + extension;
            FileHandle newFile = Gdx.files.absolute(handle.path() + File.separator + name);
            int i = 0;
            while (newFile.exists()) {
                name = filename + " " + (i++) + "." + extension;
                newFile = Gdx.files.absolute(handle.path() + File.separator + name);
            }

            return newFile;
        } else {
            throw new GdxRuntimeException("Path not a directory, path: " + path);
        }
    }
}