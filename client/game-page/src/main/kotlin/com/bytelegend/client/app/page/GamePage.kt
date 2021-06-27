@file:Suppress("DeferredResultUnused")

package com.bytelegend.client.app.page

import com.bytelegend.app.client.api.EventListener
import com.bytelegend.app.shared.Direction
import com.bytelegend.app.shared.GameInitData
import com.bytelegend.app.shared.PixelSize
import com.bytelegend.app.shared.playerAnimationSetResourceId
import com.bytelegend.app.shared.protocol.LogStreamEventData
import com.bytelegend.app.shared.protocol.logStreamEvent
import com.bytelegend.app.shared.protocol.playerEnterSceneEvent
import com.bytelegend.client.app.engine.BrowserConsoleLogger
import com.bytelegend.client.app.engine.GAME_UI_UPDATE_EVENT
import com.bytelegend.client.app.engine.Game
import com.bytelegend.client.app.engine.SCENE_LOADING_END_EVENT
import com.bytelegend.client.app.engine.SCENE_LOADING_START_EVENT
import com.bytelegend.client.app.engine.init
import com.bytelegend.client.app.engine.resource.AudioResource
import com.bytelegend.client.app.engine.resource.I18nTextResource
import com.bytelegend.client.app.engine.resource.ImageResource
import com.bytelegend.client.app.obj.HeroCharacter
import com.bytelegend.client.app.obj.uuid
import com.bytelegend.client.app.ui.AudioSwitchWidget
import com.bytelegend.client.app.ui.BannerUIComponent
import com.bytelegend.client.app.ui.CoinCountWidget
import com.bytelegend.client.app.ui.CoinCountWidgetProps
import com.bytelegend.client.app.ui.FpsCounter
import com.bytelegend.client.app.ui.GameContainer
import com.bytelegend.client.app.ui.GameContainerProps
import com.bytelegend.client.app.ui.GameModal
import com.bytelegend.client.app.ui.GameProps
import com.bytelegend.client.app.ui.GameScriptWidgetDisplayLayer
import com.bytelegend.client.app.ui.GameScriptWidgetDisplayLayerProps
import com.bytelegend.client.app.ui.HeroIndicatorWidget
import com.bytelegend.client.app.ui.ICPServerLocationWidget
import com.bytelegend.client.app.ui.LocaleSelectionDropdown
import com.bytelegend.client.app.ui.LocaleSelectionDropdownProps
import com.bytelegend.client.app.ui.MainMapCanvasLayer
import com.bytelegend.client.app.ui.MapCanvasProps
import com.bytelegend.client.app.ui.MapCoordinateTitleWidget
import com.bytelegend.client.app.ui.MapSelectionDropdown
import com.bytelegend.client.app.ui.MapSelectionDropdownProps
import com.bytelegend.client.app.ui.MapTitleWidgets
import com.bytelegend.client.app.ui.MapTitleWidgetsProps
import com.bytelegend.client.app.ui.MiniMap
import com.bytelegend.client.app.ui.MissionTitles
import com.bytelegend.client.app.ui.OnlineCounter
import com.bytelegend.client.app.ui.PlayerNames
import com.bytelegend.client.app.ui.PlayerNamesProps
import com.bytelegend.client.app.ui.ReputationCountWidget
import com.bytelegend.client.app.ui.ReputationCountWidgetProps
import com.bytelegend.client.app.ui.RightSideBar
import com.bytelegend.client.app.ui.RightSideBarProps
import com.bytelegend.client.app.ui.ScrollButtonsLayer
import com.bytelegend.client.app.ui.ScrollButtonsProps
import com.bytelegend.client.app.ui.StarCountWidget
import com.bytelegend.client.app.ui.StarCountWidgetProps
import com.bytelegend.client.app.ui.TileCursorWidget
import com.bytelegend.client.app.ui.ToastUIComponent
import com.bytelegend.client.app.ui.UserAvatarWidget
import com.bytelegend.client.app.ui.UserMouseInteractionLayer
import com.bytelegend.client.app.ui.gameChild
import com.bytelegend.client.app.ui.item.ItemWidgetProps
import com.bytelegend.client.app.ui.item.ItemsWidget
import com.bytelegend.client.app.ui.menu.Menu
import com.bytelegend.client.app.ui.menu.MenuProps
import com.bytelegend.client.app.web.toGameInitData
import kotlinx.browser.document
import kotlinx.browser.window
import react.RBuilder
import react.RComponent
import react.RElementBuilder
import react.RProps
import react.RState
import react.ReactElement
import react.dom.RDOMBuilder
import react.dom.div
import react.dom.render
import react.setState

val GAME_INIT_DATA: GameInitData = toGameInitData(window.asDynamic().gameInitData)
const val HERO_AVATAR_IMG_ID = "hero-avatar"

val game = init(GAME_INIT_DATA).apply {
    window.asDynamic().gameRuntime = this
}

fun main() {
    window.onerror = { a: dynamic, b: String, c: Int, d: Int, e: Any? ->
        BrowserConsoleLogger.error("$a $b $c $d $e")
    }

    println(playerEnterSceneEvent(""))
//    println(logStreamEvent.asDynamic())

    render(document.getElementById("app")) {
        child(GamePage::class) {
        }
    }

    window.setInterval({
        game.eventBus.emit(
            logStreamEvent("JavaIsland"),
            LogStreamEventData(
                "JavaIsland", "remember-brave-people",
                listOf(uuid(), uuid(), uuid())
            )
        )
    }, 1000)
}

interface GamePageState : RState {
    var sceneLoading: Boolean
}

interface GamePageProps : RProps

class GamePage : RComponent<GamePageProps, GamePageState>() {
    override fun GamePageState.init() {
        sceneLoading = true
    }

    init {
        loadResourcesAndStart()
        window.onresize = { onWindowResize() }
    }

    private fun loadResourcesAndStart() {
        game.resourceLoader.loadAsync(
            I18nTextResource(
                "common-${game.locale.lowercase()}",
                game.resolve("i18n/common/${game.locale.lowercase()}.json"),
                game.i18nTextContainer
            )
        )
        game.resourceLoader.loadAsync(AudioResource("forest", game.resolve("audio/forest.ogg")), false)
        game.resourceLoader.loadAsync(AudioResource("starfly", game.resolve("audio/starfly.mp3")), false)
        game.resourceLoader.loadAsync(AudioResource("popup", game.resolve("audio/popup.mp3")), false)
        game.webSocketClient.self = game.resourceLoader.loadAsync(game.webSocketClient)

        if (game.heroPlayer.isAnonymous) {
            game.sceneContainer.loadScene(GAME_INIT_DATA.initMapId) { _, _ ->
                game.start()
            }
        } else {
            val animationSetId = playerAnimationSetResourceId(GAME_INIT_DATA.player.characterId)
            val animationSetDeferred = game.resourceLoader.loadAsync(
                ImageResource(
                    animationSetId,
                    game.resolve("img/player/$animationSetId.png")
                )
            )
            game.resourceLoader.loadAsync(
                ImageResource(
                    HERO_AVATAR_IMG_ID,
                    game.heroPlayer.avatarUrl!!
                ),
                false
            )

            game.sceneContainer.loadScene(GAME_INIT_DATA.player.map) { _, newScene ->
                animationSetDeferred.await()

                val obj = HeroCharacter(newScene, GAME_INIT_DATA.player)
                game._hero = obj
                obj.init()
                newScene.objects.add(obj)
                game.start()
            }
        }
    }

    private fun onWindowResize() {
        if (!state.sceneLoading) {
            // Do nothing if page is still loading
            game.gameContainerSize = PixelSize(window.innerWidth, window.innerHeight)
            game.eventBus.emit(GAME_UI_UPDATE_EVENT, null)
        }
    }

    override fun RBuilder.render() {
        if (state.sceneLoading) {
            // some global resources like `common-en.json` still requires to be loaded
            // GameScene.load() doesn't take this into consideration
            child(LoadingPage::class) {
                attrs.eventBus = game.eventBus
            }
        } else {
            div {
                gameContainer(game) {
                    heroIndicator(attrs)
                    modalController(attrs)
                    bannerController(attrs)
                    toastController(attrs)
                    userAvatarWidget(attrs)
                    icpServerLocationWidget(attrs)
                    gameScriptWidgetDisplayLayer(attrs)
                    scrollButtons(attrs)
                    userMouseInteractionLayer(attrs)
                    mapTitleWidgets(Direction.LEFT, attrs) {
                        mapNameWidget(attrs)
                        fpsCounter(attrs)
                        onlineCounter(attrs)
                        mapCoordinateTitleWidget(attrs)
                    }
                    mapTitleWidgets(Direction.RIGHT, attrs) {
                        audioSwitch(attrs)
                        localeSelectionDropdown(attrs)
                    }
                    rightSideBarWidgets(attrs) {
                        starCountWidget(attrs)
                        coinCountWidget(attrs)
                        reputationCountWidget(attrs)
                        itemWidget(attrs)
                    }
                    checkpointTitleWidgets(attrs)
                    tileCursorWidget(attrs)
                    spriteNameWidget(attrs)
                    miniMap(attrs)
                    mapCanvas(attrs)

                    menu(attrs)
                }
            }
        }
    }

    private val gameUiUpdateEventListener: EventListener<Nothing> = {
        setState { }
    }
    private val sceneLoadingStartEventListener: EventListener<Nothing> = {
        setState { sceneLoading = true }
    }
    private val sceneLoadingEndEventListener: EventListener<Nothing> = {
        setState { sceneLoading = false }
    }

    override fun componentDidMount() {
        game.eventBus.on(GAME_UI_UPDATE_EVENT, gameUiUpdateEventListener)
        game.eventBus.on(SCENE_LOADING_START_EVENT, sceneLoadingStartEventListener)
        game.eventBus.on(SCENE_LOADING_END_EVENT, sceneLoadingEndEventListener)
    }

    override fun componentWillUnmount() {
        game.eventBus.remove(GAME_UI_UPDATE_EVENT, gameUiUpdateEventListener)
        game.eventBus.remove(SCENE_LOADING_START_EVENT, sceneLoadingStartEventListener)
        game.eventBus.remove(SCENE_LOADING_END_EVENT, sceneLoadingEndEventListener)
    }

    private fun RDOMBuilder<*>.gameContainer(game: Game, block: RElementBuilder<GameContainerProps>.() -> Unit = {}) {
        child(GameContainer::class) {
            attrs.game = game
            block()
        }
    }

    private fun RElementBuilder<GameContainerProps>.scrollButtons(
        parentProps: GameContainerProps,
        block: RElementBuilder<ScrollButtonsProps>.() -> Unit = {}
    ) = gameChild(parentProps, ScrollButtonsLayer::class, block)

    private fun RElementBuilder<GameContainerProps>.userMouseInteractionLayer(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ) = gameChild(parentProps, UserMouseInteractionLayer::class, block)

    private fun RElementBuilder<GameContainerProps>.gameScriptWidgetDisplayLayer(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameScriptWidgetDisplayLayerProps>.() -> Unit = {}
    ) = gameChild(parentProps, GameScriptWidgetDisplayLayer::class, block)

    private fun RElementBuilder<GameContainerProps>.mapCanvas(
        parentProps: GameContainerProps,
        block: RElementBuilder<MapCanvasProps>.() -> Unit = {}
    ) = gameChild(parentProps, MainMapCanvasLayer::class, block)

    private fun RElementBuilder<GameContainerProps>.miniMap(
        parentProps: GameContainerProps,
        block: RElementBuilder<MapCanvasProps>.() -> Unit = {}
    ) = gameChild(parentProps, MiniMap::class, block)

    private fun RElementBuilder<GameContainerProps>.mapTitleWidgets(
        direction: Direction,
        parentProps: GameContainerProps,
        block: RElementBuilder<MapTitleWidgetsProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, MapTitleWidgets::class) {
            attrs.direction = direction
            block()
        }
    }

    private fun RElementBuilder<GameContainerProps>.rightSideBarWidgets(
        parentProps: GameContainerProps,
        block: RElementBuilder<RightSideBarProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, RightSideBar::class) {
            block()
        }
    }

    private fun RElementBuilder<RightSideBarProps>.starCountWidget(
        parentProps: RightSideBarProps,
        block: RElementBuilder<StarCountWidgetProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, StarCountWidget::class, block)
    }

    private fun RElementBuilder<RightSideBarProps>.coinCountWidget(
        parentProps: RightSideBarProps,
        block: RElementBuilder<CoinCountWidgetProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, CoinCountWidget::class, block)
    }

    private fun RElementBuilder<RightSideBarProps>.reputationCountWidget(
        parentProps: RightSideBarProps,
        block: RElementBuilder<ReputationCountWidgetProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, ReputationCountWidget::class, block)
    }

    private fun RElementBuilder<RightSideBarProps>.itemWidget(
        parentProps: RightSideBarProps,
        block: RElementBuilder<ItemWidgetProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, ItemsWidget::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.tileCursorWidget(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, TileCursorWidget::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.checkpointTitleWidgets(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, MissionTitles::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.spriteNameWidget(
        parentProps: GameContainerProps,
        block: RElementBuilder<PlayerNamesProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, PlayerNames::class, block)
    }

    private fun RElementBuilder<MapTitleWidgetsProps>.mapNameWidget(
        parentProps: MapTitleWidgetsProps,
        block: RElementBuilder<MapSelectionDropdownProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, MapSelectionDropdown::class, block)
    }

    private fun RElementBuilder<MapTitleWidgetsProps>.fpsCounter(
        parentProps: MapTitleWidgetsProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, FpsCounter::class, block)
    }

    private fun RElementBuilder<MapTitleWidgetsProps>.onlineCounter(parentProps: MapTitleWidgetsProps, block: RElementBuilder<GameProps>.() -> Unit = {}): ReactElement {
        return gameChild(parentProps, OnlineCounter::class, block)
    }

    private fun RElementBuilder<MapTitleWidgetsProps>.mapCoordinateTitleWidget(
        parentProps: MapTitleWidgetsProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, MapCoordinateTitleWidget::class, block)
    }

    private fun RElementBuilder<MapTitleWidgetsProps>.audioSwitch(
        parentProps: MapTitleWidgetsProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, AudioSwitchWidget::class, block)
    }

    private fun RElementBuilder<MapTitleWidgetsProps>.localeSelectionDropdown(
        parentProps: MapTitleWidgetsProps,
        block: RElementBuilder<LocaleSelectionDropdownProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, LocaleSelectionDropdown::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.menu(
        parentProps: GameContainerProps,
        block: RElementBuilder<MenuProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, Menu::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.heroIndicator(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, HeroIndicatorWidget::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.modalController(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, GameModal::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.bannerController(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, BannerUIComponent::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.toastController(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, ToastUIComponent::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.userAvatarWidget(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, UserAvatarWidget::class, block)
    }

    private fun RElementBuilder<GameContainerProps>.icpServerLocationWidget(
        parentProps: GameContainerProps,
        block: RElementBuilder<GameProps>.() -> Unit = {}
    ): ReactElement {
        return gameChild(parentProps, ICPServerLocationWidget::class, block)
    }
}
