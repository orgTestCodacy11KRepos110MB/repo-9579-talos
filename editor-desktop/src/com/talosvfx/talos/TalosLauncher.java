/*******************************************************************************
 * Copyright 2019 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.talosvfx.talos;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.rockbite.bongo.engine.render.PolygonSpriteBatchMultiTextureMULTIBIND;
import com.talosvfx.talos.editor.UIStage;
import com.talosvfx.talos.editor.WorkplaceStage;
import com.talosvfx.talos.editor.layouts.LayoutApp;
import com.talosvfx.talos.editor.layouts.LayoutContent;
import com.talosvfx.talos.editor.layouts.LayoutGrid;
import com.talosvfx.talos.editor.project2.SharedResources;
import com.talosvfx.talos.editor.utils.WindowUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWDropCallback;

import static org.lwjgl.glfw.GLFW.glfwSetDropCallback;
import static org.lwjgl.system.MemoryUtil.*;

public class TalosLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1200, 900);
		config.useVsync(true);
		config.setHdpiMode(HdpiMode.Logical);
		config.setBackBufferConfig(1,1,1,1,8,8, 0);
		config.setWindowIcon("icon/talos-64x64.png");

		TalosMain2 talos = new TalosMain2() {
			@Override
			public void create () {
				super.create();
				afterCreated();
				((Lwjgl3Graphics)Gdx.graphics).setTitle("Talos - " + TalosVersion.getVersion());
			}
		};

		boolean gl3 = false;

		if (gl3) {
			config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);

			ShaderProgram.prependVertexCode = "#version 330 core\n";
			ShaderProgram.prependFragmentCode = "#version 330 core\n";
		}
		



//		GLFWWindowFocusCallback glfwWindowFocusCallback = GLFWWindowFocusCallback.create(new GLFWWindowFocusCallback() {
//			@Override
//			public void invoke (long window, boolean focused) {
//				Gdx.app.postRunnable(new Runnable() {
//					@Override
//					public void run () {
//						TalosMain.focused = focused;
//					}
//				});
//			}
//		});
//		GLFW.glfwSetWindowFocusCallback(((Lwjgl3Graphics)Gdx.graphics).getWindow().getWindowHandle(), glfwWindowFocusCallback);

		SharedResources.windowUtils = new WindowUtils() {
			@Override
			public void openWindow (LayoutApp layoutApp) {
				Lwjgl3Graphics graphics = (Lwjgl3Graphics)Gdx.graphics;
				Lwjgl3Application lwjgl3App = (Lwjgl3Application)Gdx.app;

				config.setWindowedMode(500, 500);

				Lwjgl3Window window = lwjgl3App.newWindow(new ApplicationAdapter() {

					private Stage stage;

					@Override
					public void create () {
						super.create();

						stage = new Stage(new ScreenViewport(), new PolygonSpriteBatchMultiTextureMULTIBIND());
						SharedResources.inputHandling.addPermanentInputProcessor(stage);
						SharedResources.inputHandling.setGDXMultiPlexer();


						Table layoutGridContainer = new Table();
						layoutGridContainer.setFillParent(true);

						LayoutGrid layoutGrid = new LayoutGrid(SharedResources.skin);

						layoutGridContainer.clearChildren();
						layoutGridContainer.add(layoutGrid).grow();

						stage.addActor(layoutGridContainer);

						layoutGrid.addContent(new LayoutContent(SharedResources.skin, layoutGrid, layoutApp));
					}

					@Override
					public void render () {
						super.render();
						ScreenUtils.clear(0, 0, 0, 1f, true);

						stage.act();
						stage.draw();

					}

					@Override
					public void resize (int width, int height) {
						super.resize(width, height);
						stage.getViewport().update(width, height, true);
					}
				}, config);
			}

		};

		new Lwjgl3Application(talos, config);
	}

	private static void afterCreated () {
		final Lwjgl3Graphics graphics = (Lwjgl3Graphics)Gdx.graphics;
		glfwSetDropCallback(graphics.getWindow().getWindowHandle(), new GLFWDropCallback() {
			@Override
			public void invoke (long window, int count, long names) {

				PointerBuffer namebuffer = memPointerBuffer(names, count);
				final String[] filesPaths = new String[count];
				for (int i = 0; i < count; i++) {
					String pathToObject = memUTF8(memByteBufferNT1(namebuffer.get(i)));
					filesPaths[i] = pathToObject;
				}

				Gdx.app.postRunnable(new Runnable() {
					@Override
					public void run () {
						final int x = Gdx.input.getX();
						final int y = Gdx.input.getY();

						SharedResources.globalDragAndDrop.fakeDragDrop(x, y, filesPaths);
					}
				});
			}
		});
	}
}