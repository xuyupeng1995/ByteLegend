/*
 * Copyright 2021 ByteLegend Technologies and the original author or authors.
 *
 * Licensed under the GNU Affero General Public License v3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://github.com/ByteLegend/ByteLegend/blob/master/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytelegend.client.app.engine

import com.bytelegend.app.client.api.GameRuntime
import com.bytelegend.app.client.utils.JSObjectBackedMap
import com.bytelegend.app.shared.GameMap
import com.bytelegend.app.shared.objects.GameMapMission
import com.bytelegend.client.app.engine.resource.ItemAchievementMetadataResource
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import org.kodein.di.DI
import org.kodein.di.instance

/**
 * The metadata for the item.
 */
data class ItemOrAchievementMetadata(
    val id: String,
    val icon: String,
    val nameTextId: String,
    val descTextId: String
)

/**
 * An item may be able to applied to a specific mission,
 * for example, the key to open chest 1, the key to open chest 2, etc.
 * In this way, it's stored as "{itemId}:{mapId}:{missionId}", e.g. "key:JavaIsland:install-java-ide" or "gold-sword:JavaIsland:create-a-new-class"
 */
data class Item(
    val metadata: ItemOrAchievementMetadata,
    val mission: GameMapMission? = null
) {
    override fun toString() = if (mission == null) metadata.id else "${metadata.id}:${mission.map}:${mission.id}"
}

interface ItemAchievementManager {

    /**
     * Because the mission referenced in item might not be loaded yet (in another scene),
     * we have to load the game map and i18n texts if we need to know the mission title.
     *
     * Invoking this method will trigger loading GameMap and i18n text for that map.
     */
    suspend fun getItems(): Map<String, Item>

    suspend fun getAchievements(): Map<String, Item>
}

/**
 * Manage the items.
 */
class DefaultItemAchievementManager(private val di: DI) : ItemAchievementManager {
    private val game: Game by lazy {
        val gameRuntime: GameRuntime by di.instance()
        gameRuntime.unsafeCast<Game>()
    }
    private val sceneContainer: DefaultGameSceneContainer = game.sceneContainer.unsafeCast<DefaultGameSceneContainer>()

    private val itemMetadata: MutableMap<String, ItemOrAchievementMetadata> = JSObjectBackedMap()
    private val achievementMetadata: MutableMap<String, ItemOrAchievementMetadata> = JSObjectBackedMap()
    private val items: MutableMap<String, Item> = JSObjectBackedMap()
    private val achievements: MutableMap<String, Item> = JSObjectBackedMap()

    override suspend fun getAchievements(): Map<String, Item> {
        if (game.heroPlayer.achievements.size == achievements.size) {
            return achievements
        }
        if (achievementMetadata.isEmpty()) {
            achievementMetadata.putAll(game.resourceLoader.loadAsync(ItemAchievementMetadataResource("achievementMetadata", game.resolve("/misc/achievement-metadata.json")), false).await())
        }
        game.heroPlayer.achievements.forEach {
            achievements[it] = Item(achievementMetadata.getValue(it))
        }
        return achievements
    }

    override suspend fun getItems(): Map<String, Item> {
        if (game.heroPlayer.items.size == items.size) {
            return items
        }
        if (itemMetadata.isEmpty()) {
            itemMetadata.putAll(game.resourceLoader.loadAsync(ItemAchievementMetadataResource("itemMetadata", game.resolve("/misc/item-metadata.json")), false).await())
        }
        val loadingMaps = mutableListOf<Deferred<*>>()
        val unresolvedItems = mutableListOf<String>()
        game.heroPlayer.items.forEach {
            val unresolvedMap = resolveItem(it)
            if (unresolvedMap != null) {
                loadingMaps.add(sceneContainer.loadGameMap(unresolvedMap, false))
                loadingMaps.add(sceneContainer.loadI18nResource(unresolvedMap, false))
                unresolvedItems.add(it)
            }
        }

        loadingMaps.awaitAll()
        unresolvedItems.forEach {
            resolveItem(it)
        }
        return items
    }

    /**
     * Resolve mission from an item and put into `items`.
     *
     * Return `null` if mission is resolved successfully,
     * otherwise return the map id of unresolved mission.
     */
    private fun resolveItem(itemWithMissionId: String): String? {
        if (items.containsKey(itemWithMissionId)) {
            return null
        }
        val itemMapMissionId = itemWithMissionId.split(":")
        if (itemMapMissionId.size == 1) {
            items[itemWithMissionId] = Item(itemMetadata.getValue(itemWithMissionId))
            return null
        } else if (itemMapMissionId.size > 2) {
            val mapId = itemMapMissionId[1]
            val gameMap = game.resourceLoader.getLoadedResourceOrNull<GameMap>(mapJsonResourceId(mapId))
            val i18n = game.resourceLoader.getLoadedResourceOrNull<Any>(mapI18nResourceId(mapId, game.locale))
            return if (gameMap == null || i18n == null) {
                mapId
            } else {
                val missionId = itemMapMissionId[2]
                val item = Item(itemMetadata.getValue(itemMapMissionId[0]), gameMap.objects.first { it.id == missionId }.unsafeCast<GameMapMission>())
                items[itemWithMissionId] = item
                null
            }
        } else {
            console.warn("Unrecognized item: $itemWithMissionId")
            return null
        }
    }
}
