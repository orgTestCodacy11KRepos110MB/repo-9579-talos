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

package com.rockbite.talos.runtime.test.utils;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.rockbite.tools.talos.runtime.assets.BaseAssetProvider;

public class TestAssetProvider extends BaseAssetProvider {

	private final TextureAtlas atlas;

	public TestAssetProvider (final TextureAtlas atlas) {
		this.atlas = atlas;

		setAssetHandler(TextureRegion.class, new AssetHandler<TextureRegion>() {
			@Override
			public TextureRegion findAsset (String assetName) {
				return atlas.findRegion(assetName);
			}
		});

		setAssetHandler(Sprite.class, new AssetHandler<Sprite>() {
			@Override
			public Sprite findAsset (String assetName) {
				return atlas.createSprite(assetName);
			}
		});
	}
}
