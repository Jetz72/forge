package forge.adventure.stage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScalingViewport;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.ItemData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.scene.DeckSelectScene;
import forge.adventure.scene.GameScene;
import forge.adventure.scene.InventoryScene;
import forge.adventure.scene.MapViewScene;
import forge.adventure.scene.QuestLogScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.adventure.util.KeyBinding;
import forge.adventure.util.UIActor;
import forge.adventure.world.WorldSave;
import forge.deck.Deck;
import forge.gui.GuiBase;
import forge.localinstance.properties.ForgePreferences;
import forge.model.FModel;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Stage to handle everything rendered in the HUD
 */
public class GameHUD extends Stage {

    static public GameHUD instance;
    private final GameStage gameStage;
    private final Image avatar, miniMapPlayer;
    private final TypingLabel keyCollection;
    private final TypingLabel lifePoints;
    private final TypingLabel money;
    private final TypingLabel shards;
    private final TextraLabel notificationText = Controls.newTextraLabel("");
    private final Image miniMap, gamehud, mapborder, avatarborder, blank;
    private final InputEvent eventTouchDown, eventTouchUp;
    private final TextraButton deckActor, openMapActor, menuActor, logbookActor, inventoryActor, exitToWorldMapActor, bookmarkActor;
    public final UIActor ui;
    private final Touchpad touchpad;
    private final Console console;
    float TOUCHPAD_SCALE = 70f, referenceX;
    float opacity = 1f;
    private boolean debugMap, transluscent, hidden;

    private final Dialog dialog;
    private boolean dialogOnlyInput;
    private final Array<TextraButton> dialogButtonMap = new Array<>();
    private final Array<TextraButton> abilityButtonMap = new Array<>();
    private String lifepointsTextColor = "";
    private final ScrollPane notificationPane;
    private final Group mapGroup = new Group();
    private final Group hudGroup = new Group();
    private final Group menuGroup = new Group();
    private final Group avatarGroup = new Group();

    private GameHUD(GameStage gameStage) {
        super(new ScalingViewport(Scaling.stretch, Scene.getIntendedWidth(), Scene.getIntendedHeight()), gameStage.getBatch());
        instance = this;
        this.gameStage = gameStage;

        ui = new UIActor(Config.instance().getFile(GuiBase.isAndroid()
                ? Forge.isLandscapeMode() ? "ui/hud_landscape.json" : "ui/hud_portrait.json"
                : Forge.isLandscapeMode() ? "ui/hud.json" : "ui/hud_portrait.json"));


        blank = ui.findActor("blank");
        miniMap = ui.findActor("map");
        mapborder = ui.findActor("mapborder");

        avatarborder = ui.findActor("avatarborder");
        deckActor = ui.findActor("deck");
        openMapActor = ui.findActor("openmap");
        ui.onButtonPress("openmap", this::openMap);
        menuActor = ui.findActor("menu");
        referenceX = menuActor.getX();
        logbookActor = ui.findActor("logbook");
        inventoryActor = ui.findActor("inventory");
        gamehud = ui.findActor("gamehud");
        exitToWorldMapActor = ui.findActor("exittoworldmap");
        bookmarkActor = ui.findActor("bookmark");
        dialog = Controls.newDialog("");

        miniMapPlayer = new Image(Forge.getAssets().getTexture(Config.instance().getFile("ui/minimap_player.png")));
        //create touchpad
        touchpad = new Touchpad(10, Controls.getSkin());
        touchpad.setBounds(15, 15, TOUCHPAD_SCALE, TOUCHPAD_SCALE);
        touchpad.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent changeEvent, Actor actor) {
                if (MapStage.getInstance().isInMap()) {
                    if (MapStage.getInstance().isPaused())
                        return;
                    MapStage.getInstance().getPlayerSprite().getMovementDirection().x += ((Touchpad) actor).getKnobPercentX();
                    MapStage.getInstance().getPlayerSprite().getMovementDirection().y += ((Touchpad) actor).getKnobPercentY();
                } else {
                    if (WorldStage.getInstance().isPaused())
                        return;
                    WorldStage.getInstance().getPlayerSprite().getMovementDirection().x += ((Touchpad) actor).getKnobPercentX();
                    WorldStage.getInstance().getPlayerSprite().getMovementDirection().y += ((Touchpad) actor).getKnobPercentY();
                }
            }
        });
        if (GuiBase.isAndroid()) //add touchpad for android
            ui.addActor(touchpad);

        avatar = ui.findActor("avatar");
        ui.onButtonPress("menu", this::menu);
        ui.onButtonPress("inventory", this::openInventory);
        ui.onButtonPress("logbook", this::logbook);
        ui.onButtonPress("deck", this::openDeck);
        ui.onButtonPress("exittoworldmap", this::exitToWorldMap);
        ui.onButtonPress("bookmark", this::bookmark);
        keyCollection = ui.findActor("keyCollection");
        keyCollection.skipToTheEnd();
        lifePoints = ui.findActor("lifePoints");
        lifePoints.skipToTheEnd();
        shards = ui.findActor("shards");
        shards.skipToTheEnd();
        money = ui.findActor("money");
        money.skipToTheEnd();
        shards.setText("[%95][+Shards]");
        money.setText("[%95][+Gold]");
        lifePoints.setText("[%95][+Life]");
        AdventurePlayer.current().onLifeChange(() -> {
            String effect = "{EMERGE}";
            String effectEnd = "{ENDEMERGE}";
            String heartbeat = "";
            //colored lifepoints
            if (Current.player().getLife() >= Current.player().getMaxLife()) {
                //color green if max life
                lifepointsTextColor = "[GREEN]";
            } else if (Current.player().getLife() <= 5) {
                //color red if critical
                effect = "";
                effectEnd = "";
                heartbeat = "{HEARTBEAT=0.5;0.5}";
                lifepointsTextColor = "{ENDHEARTBEAT}[RED]";
            } else {
                lifepointsTextColor = "[WHITE]";
            }
            lifePoints.restart("[%95]" + heartbeat + "[+Life]" + lifepointsTextColor + effect + " " + AdventurePlayer.current().getLife() + effectEnd + "/" + AdventurePlayer.current().getMaxLife());
        });
        AdventurePlayer.current().onShardsChange(() -> shards.restart("[%95][+Shards]{EMERGE} " + AdventurePlayer.current().getShards() + "{ENDEMERGE}"));
        AdventurePlayer.current().onGoldChange(() -> money.restart("[%95][+Gold]{EMERGE} " + AdventurePlayer.current().getGold() + "{ENDEMERGE}"));
        AdventurePlayer.current().onEquipmentChanged(this::updateAbility);
        addActor(ui);
        addActor(miniMapPlayer);
        console = new Console();
        console.setBounds(0, GuiBase.isAndroid() ? getHeight() : 0, getWidth(), getHeight() / 2);
        console.setVisible(false);
        ui.addActor(console);
        avatar.addListener(new ConsoleToggleListener());
        avatarborder.addListener(new ConsoleToggleListener());
        gamehud.addListener(new ConsoleToggleListener());
        WorldSave.getCurrentSave().onLoad(this::enter);

        eventTouchDown = new InputEvent();
        eventTouchDown.setPointer(-1);
        eventTouchDown.setType(InputEvent.Type.touchDown);
        eventTouchUp = new InputEvent();
        eventTouchUp.setPointer(-1);
        eventTouchUp.setType(InputEvent.Type.touchUp);

        notificationPane = new ScrollPane(notificationText);
        notificationPane.setTouchable(Touchable.childrenOnly);
        notificationPane.setBounds(5, GuiBase.isAndroid() ? getHeight() : -notificationText.getPrefHeight(), getWidth() * 0.4f, 25);
        notificationPane.setStyle(Controls.getSkin().get("paper", ScrollPane.ScrollPaneStyle.class));
        notificationPane.getColor().a = 0f;

        ui.addActor(notificationPane);
        //MAP
        mapGroup.addActor(miniMap);
        mapGroup.addActor(mapborder);
        mapGroup.addActor(openMapActor);
        mapGroup.addActor(miniMapPlayer);
        ui.addActor(mapGroup);
        //HUD
        hudGroup.addActor(gamehud);
        hudGroup.addActor(keyCollection);
        hudGroup.addActor(lifePoints);
        hudGroup.addActor(shards);
        hudGroup.addActor(money);
        hudGroup.addActor(blank);
        ui.addActor(hudGroup);
        //MENU
        menuGroup.addActor(deckActor);
        menuGroup.addActor(menuActor);
        menuGroup.addActor(logbookActor);
        menuGroup.addActor(inventoryActor);
        menuGroup.addActor(exitToWorldMapActor);
        menuGroup.addActor(bookmarkActor);
        ui.addActor(menuGroup);
        //AVATAR
        avatarGroup.addActor(avatar);
        avatarGroup.addActor(avatarborder);
        ui.addActor(avatarGroup);
    }

    private void openMap() {
        if (console.isVisible())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        Forge.switchScene(MapViewScene.instance());
    }

    private void logbook() {
        if (console.isVisible())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        Forge.switchScene(QuestLogScene.instance(Forge.getCurrentScene()));
    }

    public static GameHUD getInstance() {
        return instance == null ? instance = new GameHUD(WorldStage.getInstance()) : instance;
    }

    public Touchpad getTouchpad() {
        return touchpad;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        touchpad.setVisible(false);
        MapStage.getInstance().getPlayerSprite().setMovementDirection(Vector2.Zero);
        WorldStage.getInstance().getPlayerSprite().setMovementDirection(Vector2.Zero);
        return super.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        Vector2 c = new Vector2();
        screenToStageCoordinates(c.set(screenX, screenY));

        float x = (c.x - miniMap.getX()) / miniMap.getWidth();
        float y = (c.y - miniMap.getY()) / miniMap.getHeight();
        //map bounds
        if (Controls.actorContainsVector(miniMap, c)) {
            touchpad.setVisible(false);
            if (mapGroup.isVisible() && debugMap) {
                WorldStage.getInstance().getPlayerSprite().setPosition(x * WorldSave.getCurrentSave().getWorld().getWidthInPixels(), y * WorldSave.getCurrentSave().getWorld().getHeightInPixels());
            } else if (!mapGroup.isVisible()) {
                return false;
            }

            return true;
        }
        return super.touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector2 c = new Vector2();
        Vector2 touch = new Vector2();
        screenToStageCoordinates(touch.set(screenX, screenY));
        screenToStageCoordinates(c.set(screenX, screenY));

        float x = (c.x - miniMap.getX()) / miniMap.getWidth();
        float y = (c.y - miniMap.getY()) / miniMap.getHeight();
        if (Controls.actorContainsVector(gamehud, c)) {
            super.touchDown(screenX, screenY, pointer, button);
            return true;
        }
        if (Controls.actorContainsVector(miniMap, c)) {
            if (mapGroup.isVisible() && debugMap) {
                WorldStage.getInstance().getPlayerSprite().setPosition(x * WorldSave.getCurrentSave().getWorld().getWidthInPixels(), y * WorldSave.getCurrentSave().getWorld().getHeightInPixels());
            } else if (!mapGroup.isVisible()) {
                // in this case, we want to send an action to the scene, which happens in the caller
                return false;
            }
            return true;
        }
        //auto follow touchpad
        if (GuiBase.isAndroid() && !MapStage.getInstance().isDialogOnlyInput() && !console.isVisible()) {
            if (!(Controls.actorContainsVector(avatar, touch)) // not inside avatar bounds
                    && !(Controls.actorContainsVector(miniMap, touch)) // not inside map bounds
                    && !(Controls.actorContainsVector(gamehud, touch)) //not inside gamehud bounds
                    && !(Controls.actorContainsVector(menuActor, touch)) //not inside menu button
                    && !(Controls.actorContainsVector(deckActor, touch)) //not inside deck button
                    && !(Controls.actorContainsVector(openMapActor, touch)) //not inside openmap button
                    && !(Controls.actorContainsVector(logbookActor, touch)) //not inside stats button
                    && !(Controls.actorContainsVector(inventoryActor, touch)) //not inside inventory button
                    && !(Controls.actorContainsVector(exitToWorldMapActor, touch)) //not inside exit button
                    && !(Controls.actorContainsVector(bookmarkActor, touch)) //not inside bookmark button
                    && !(Controls.actorContainsVector(abilityButtonMap, touch)) //not inside abilityButtonMap
                    && (Controls.actorContainsVector(ui, touch)) //inside display bounds
                    && pointer < 1) { //not more than 1 pointer
                touchpad.setBounds(touch.x - TOUCHPAD_SCALE / 2, touch.y - TOUCHPAD_SCALE / 2, TOUCHPAD_SCALE, TOUCHPAD_SCALE);
                touchpad.setVisible(true);
                touchpad.setResetOnTouchUp(true);
                return super.touchDown(screenX, screenY, pointer, button);
            }
        }
        return super.touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public void draw() {
        int yPos = (int) gameStage.player.getY();
        int xPos = (int) gameStage.player.getX();
        act(Gdx.graphics.getDeltaTime()); //act the Hud
        super.draw(); //draw the Hud
        int xPosMini = (int) (((float) xPos / (float) WorldSave.getCurrentSave().getWorld().getTileSize() / (float) WorldSave.getCurrentSave().getWorld().getWidthInTiles()) * miniMap.getWidth());
        int yPosMini = (int) (((float) yPos / (float) WorldSave.getCurrentSave().getWorld().getTileSize() / (float) WorldSave.getCurrentSave().getWorld().getHeightInTiles()) * miniMap.getHeight());
        miniMapPlayer.setPosition(miniMap.getX() + xPosMini - miniMapPlayer.getWidth() / 2, miniMap.getY() + yPosMini - miniMapPlayer.getHeight() / 2);

        miniMapPlayer.setVisible(miniMap.isVisible() &&
                !Controls.actorContainsVector(notificationPane, new Vector2(miniMapPlayer.getX(), miniMapPlayer.getY()))
                && (!Controls.actorContainsVector(console, new Vector2(miniMapPlayer.getX(), miniMapPlayer.getY()))
                || !console.isVisible())); // prevent drawing on top of console or notifications

        if (!MapStage.getInstance().isInMap())
            updateMusic();
        else
            SoundSystem.instance.pause();
    }

    Texture miniMapTexture;
    Texture miniMapToolTipTexture;
    Pixmap miniMapToolTipPixmap;
    public boolean fromWorldMap = false;

    public void enter() {
        updateKeys();
        if (miniMapTexture != null)
            miniMapTexture.dispose();
        miniMapTexture = new Texture(WorldSave.getCurrentSave().getWorld().getBiomeImage());
        if (miniMapToolTipTexture != null)
            miniMapToolTipTexture.dispose();
        if (miniMapToolTipPixmap != null)
            miniMapToolTipPixmap.dispose();
        miniMapToolTipPixmap = new Pixmap((int) (miniMap.getWidth() * 3), (int) (miniMap.getHeight() * 3), Pixmap.Format.RGBA8888);
        miniMapToolTipPixmap.drawPixmap(WorldSave.getCurrentSave().getWorld().getBiomeImage(), 0, 0, WorldSave.getCurrentSave().getWorld().getBiomeImage().getWidth(), WorldSave.getCurrentSave().getWorld().getBiomeImage().getHeight(), 0, 0, miniMapToolTipPixmap.getWidth(), miniMapToolTipPixmap.getHeight());
        miniMapToolTipTexture = new Texture(miniMapToolTipPixmap);
        miniMap.setDrawable(new TextureRegionDrawable(miniMapTexture));
        avatar.setDrawable(new TextureRegionDrawable(Current.player().avatar()));
        Deck deck = AdventurePlayer.current().getSelectedDeck();

        switch (GameScene.instance().getAdventurePlayerLocation(false, false)) {
            case "capital":
            case "town":
                if (MapStage.getInstance().isInMap()) {
                    int rep = TileMapScene.instance().getPointOfInterestChanges().getMapReputation();
                    String reputationText = TileMapScene.instance().rootPoint.getDisplayName() + "\nReputation: " + (rep > 0 ? "[GREEN]" : rep < 0 ? "[RED]" : "[WHITE]") + rep + "[/]";
                    if (fromWorldMap) {
                        addNotification(reputationText);
                        fromWorldMap = false;
                    }
                }
                break;
            case "dungeon":
            case "cave":
            case "castle":
                if (fromWorldMap) {
                    addNotification(TileMapScene.instance().rootPoint.getDisplayName());
                    fromWorldMap = false;
                }
                break;
            default:
                break;
        }

        if (deck == null || deck.isEmpty() || deck.getMain().toFlatList().size() < 30) {
            deckActor.setColor(Color.RED);
        } else {
            deckActor.setColor(menuActor.getColor());
        }
        if (MapStage.getInstance().isInMap()) {
            SoundSystem.instance.pause();
            playAudio();
        } else {
            unloadAudio();
            SoundSystem.instance.resume(); // resume World BGM
        }
        //unequip and reequip abilities
        updateAbility();
        restorePlayerCollision();
        if (openMapActor != null) {
            String val = "[%80]" + Forge.getLocalizer().getMessageorUseDefault("lblZoom", "Zoom");
            for (AdventureQuestData adq : Current.player().getQuests()) {
                if (adq.getTargetPOI() != null) {
                    val = "[%80][+GPS] " + Forge.getLocalizer().getMessageorUseDefault("lblZoom", "Zoom");
                    break;
                }
            }
            openMapActor.setText(val);
            openMapActor.layout();
        }
        if (MapStage.getInstance().isInMap())
            updateBookmarkActor(MapStage.getInstance().getChanges().isBookmarked());
        avatarGroup.setZIndex(ui.getChildren().size);
    }

    void updateKeys() {
        String keys = "";
        keys += AdventurePlayer.current().hasItem("Red Key") ? "[+RedKey]\n" : "[+Dot]\n";
        keys += AdventurePlayer.current().hasItem("Green Key") ? "[+GreenKey]\n" : "[+Dot]\n";
        keys += AdventurePlayer.current().hasItem("Blue Key") ? "[+BlueKey]\n" : "[+Dot]\n";
        keys += AdventurePlayer.current().hasItem("Black Key") ? "[+BlackKey]\n" : "[+Dot]\n";
        keys += AdventurePlayer.current().hasItem("White Key") ? "[+WhiteKey]\n" : "[+Dot]\n";
        keys += AdventurePlayer.current().hasItem("Strange Key") ? "[+StrangeKey]" : "[+Dot]";
        keyCollection.setText(keys);
    }

    void clearAbility() {
        for (TextraButton button : abilityButtonMap) {
            button.remove();
        }
        abilityButtonMap.clear();
    }

    void updateAbility() {
        clearAbility();
        setAbilityButton(AdventurePlayer.current().getEquippedAbility1());
        setAbilityButton(AdventurePlayer.current().getEquippedAbility2());
        float x = Forge.isLandscapeMode() ? 426f : 216f;
        float y = Forge.isLandscapeMode() ? 10f : 60f;
        float w = 45f;
        float h = 35f;
        for (TextraButton button : abilityButtonMap) {
            button.getColor().a = opacity;
            button.setSize(w, h);
            button.setPosition(x, y);
            y += h + 15f;
            addActor(button);
        }
    }

    void setAbilityButton(ItemData data) {
        if (data != null) {
            TextraButton button = Controls.newTextButton("[%90][+" + data.iconName + "][+Shards][BLACK]" + data.shardsNeeded, () -> {
                if (console.isVisible())
                    return;
                if (Forge.advFreezePlayerControls)
                    return;
                boolean isInPoi = MapStage.getInstance().isInMap();
                if (!(isInPoi && data.usableInPoi || !isInPoi && data.usableOnWorldMap))
                    return;
                if (data.shardsNeeded > Current.player().getShards())
                    return;
                Current.player().addShards(-data.shardsNeeded);
                ConsoleCommandInterpreter.getInstance().command(data.commandOnUse);
                AdventureQuestController.instance().updateItemUsed(data);
            }, "menu");
            abilityButtonMap.add(button);
        }
    }

    private Pair<FileHandle, Music> audio = null;

    public void switchAudio() {
        if (MapStage.getInstance().isInMap()) {
            pauseMusic();
            playAudio();
        }
    }

    public void playAudio() {
        switch (GameScene.instance().getAdventurePlayerLocation(false, false)) {
            case "capital":
            case "town":
                setAudio(MusicPlaylist.TOWN);
                break;
            case "dungeon":
            case "cave":
                setAudio(MusicPlaylist.CAVE);
                break;
            case "castle":
                setAudio(MusicPlaylist.CASTLE);
                break;
            default:
                break;
        }
        if (audio != null) {
            audio.getRight().setLooping(true);
            audio.getRight().play();
            audio.getRight().setVolume(FModel.getPreferences().getPrefInt(ForgePreferences.FPref.UI_VOL_MUSIC) / 100f);
        }
    }

    public void fadeAudio(float value) {
        if (audio != null) {
            audio.getRight().setVolume((FModel.getPreferences().getPrefInt(ForgePreferences.FPref.UI_VOL_MUSIC) * value) / 100f);
        }
    }

    public boolean audioIsPlaying() {
        if (audio == null)
            return false;
        return audio.getRight().isPlaying();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (fade < targetfade) {
            fade += (delta / 2);
            if (fade > targetfade)
                fade = targetfade;
            fadeAudio(fade);
        } else if (fade > targetfade) {
            fade -= (delta / 2);
            if (fade < targetfade)
                fade = targetfade;
            fadeAudio(fade);
        }
    }

    float fade = 1f;
    float targetfade = 1f;

    public void fadeIn() {
        targetfade = 1f;
    }

    public void fadeOut() {
        targetfade = 0.1f;
    }

    public void stopAudio() {
        if (audio != null) {
            audio.getRight().stop();
        }
    }

    public void pauseMusic() {
        if (audio != null) {
            audio.getRight().pause();
        }
        SoundSystem.instance.pause();
    }

    public void unloadAudio() {
        if (audio != null) {
            audio.getRight().setOnCompletionListener(null);
            audio.getRight().stop();
            Forge.getAssets().manager().unload(audio.getLeft().path());
        }
        audio = null;
        currentAudioPlaylist = null;
    }

    private MusicPlaylist currentAudioPlaylist = null;

    private void setAudio(MusicPlaylist playlist) {
        if (playlist.equals(currentAudioPlaylist))
            return;
        //System.out.println("Playlist: "+playlist);
        unloadAudio();
        //System.out.println("Playlist: "+playlist);
        audio = getMusic(playlist);
    }

    private Pair<FileHandle, Music> getMusic(MusicPlaylist playlist) {
        String filename = playlist.getNewRandomFilename();
        if (filename == null)
            return null;
        FileHandle file = Gdx.files.absolute(filename);
        Music music = Forge.getAssets().getMusic(file);
        if (music != null) {
            currentAudioPlaylist = playlist;
            return Pair.of(file, music);
        } else {
            currentAudioPlaylist = null;
            return null;
        }
    }

    private void openDeck() {
        if (console.isVisible())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        Forge.switchScene(DeckSelectScene.instance());
    }

    private void openInventory() {
        if (console.isVisible())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        WorldSave.getCurrentSave().header.createPreview();
        Forge.switchScene(InventoryScene.instance());
    }

    private void exitToWorldMap() {
        if (console.isVisible())
            return;
        if (!MapStage.getInstance().isInMap()) //prevent showing this dialog to WorldMap
            return;
        if (!MapStage.getInstance().canEscape())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        dialog.getButtonTable().clear();
        dialog.getContentTable().clear();
        dialog.clearListeners();
        TextraButton YES = Controls.newTextButton(Forge.getLocalizer().getMessage("lblYes"), this::exitDungeonCallback);
        TextraButton NO = Controls.newTextButton(Forge.getLocalizer().getMessage("lblNo"), this::hideDialog);
        TypingLabel L = Controls.newTypingLabel(Forge.getLocalizer().getMessageorUseDefault("lblExitToWoldMap", "Exit to the World Map?"));
        L.setWrap(true);
        L.skipToTheEnd();
        dialog.getButtonTable().add(YES).width(60f);
        dialog.getButtonTable().add(NO).width(60f);
        dialog.getContentTable().add(L).width(120f);
        dialog.setKeepWithinStage(true);
        showDialog();
    }

    private void bookmark() {
        if (console.isVisible())
            return;
        if (!MapStage.getInstance().isInMap())
            return;
        if (!MapStage.getInstance().canEscape())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        if (MapStage.getInstance().isInMap()) {
            if (MapStage.getInstance().getChanges().isBookmarked()) {
                MapStage.getInstance().getChanges().setIsBookmarked(false);
                PointOfInterestMapSprite mapSprite = WorldStage.getInstance().getMapSprite(GameScene.instance().getMapPOI());
                if (mapSprite != null) {
                    MapStage.getInstance().getChanges().save();
                    mapSprite.setBookmarked(false, mapSprite.getPointOfInterest());
                    updateBookmarkActor(false);
                }
            } else {
                MapStage.getInstance().getChanges().setIsBookmarked(true);
                PointOfInterestMapSprite mapSprite = WorldStage.getInstance().getMapSprite(GameScene.instance().getMapPOI());
                if (mapSprite != null) {
                    MapStage.getInstance().getChanges().save();
                    mapSprite.setBookmarked(true, mapSprite.getPointOfInterest());
                    updateBookmarkActor(true);
                }
            }
        }
    }

    private void updateBookmarkActor(boolean value) {
        if (bookmarkActor == null)
            return;
        bookmarkActor.setText("[%120][+" + (value ? "Bookmark" : "Unmark") + "]");
    }

    private void exitDungeonCallback() {
        MapStage.getInstance().onBeginLeavingDungeon();
        hideDialog(true);
    }

    private void hideDialog() {
        hideDialog(false);
    }

    private void menu() {
        if (console.isVisible())
            return;
        if (Forge.advFreezePlayerControls)
            return;
        gameStage.openMenu();
    }

    private void setDisabled(Actor actor, boolean value, String enabled, String disabled) {
        if (actor instanceof TextraButton) {
            ((TextraButton) actor).setDisabled(value);
            ((TextraButton) actor).setText(((TextraButton) actor).isDisabled() ? disabled : enabled);
        }
    }

    private void setAlpha(Actor actor, boolean visible) {
        if (actor != null) {
            if (visible)
                actor.addAction(Actions.alpha(1f, 0.5f));
            else
                actor.addAction(Actions.alpha(actor == mapGroup ? 0f : 0.4f, 0.5f));
        }
    }

    public void showHideMap(boolean visible) {
        transluscent = !visible;
        setAlpha(mapGroup, visible);
        mapGroup.setVisible(visible);
        setAlpha(hudGroup, visible);
        setAlpha(menuGroup, visible);
        setAlpha(avatarGroup, visible);

        setDisabled(exitToWorldMapActor, !MapStage.getInstance().isInMap(), "[%120][+ExitToWorldMap]", "\u2613");
        setDisabled(bookmarkActor, !MapStage.getInstance().isInMap(), "[%120][+Bookmark]", "\u2613");

        for (TextraButton button : abilityButtonMap) {
            setAlpha(button, visible);
        }
        opacity = visible ? 1f : 0.4f;
    }

    public void setHUDOpacity(boolean translucent) {
        if (translucent) {
            if (!MapStage.getInstance().isInMap())
                return; //WorldStage opacity issue
            setAlpha(hudGroup, false);
            setAlpha(menuGroup, false);
            setAlpha(avatarGroup, false);
            for (TextraButton button : abilityButtonMap) {
                setAlpha(button, false);
            }
            transluscent = true;
        } else {
            setAlpha(hudGroup, true);
            setAlpha(menuGroup, true);
            setAlpha(avatarGroup, true);
            for (TextraButton button : abilityButtonMap) {
                setAlpha(button, true);
            }
            transluscent = false;
        }
    }

    public void showHideHUD(boolean hide) {
        if (hide) {
            hudGroup.addAction(Actions.fadeOut(0.5f));
            menuGroup.addAction(Actions.fadeOut(0.5f));
            if (!MapStage.getInstance().isInMap()) {
                mapGroup.addAction(Actions.fadeOut(0.5f));
                mapGroup.addAction(Actions.visible(false));
            }
            if (MapStage.getInstance().isInMap())
                avatarGroup.addAction(Actions.alpha(0.4f, 0.5f));
            hidden = true;
        } else {
            float alpha = MapStage.getInstance().isInMap() ? 0.4f : 1f;
            avatarGroup.addAction(Actions.alpha(alpha, 0.5f));
            hudGroup.addAction(Actions.alpha(alpha, 0.5f));
            menuGroup.addAction(Actions.alpha(alpha, 0.5f));
            if (!MapStage.getInstance().isInMap()) {
                mapGroup.addAction(Actions.fadeIn(0.5f));
                mapGroup.addAction(Actions.visible(true));
            }
            hidden = false;
        }
    }

    void toggleConsole() {
        console.toggle();
        if (console.isVisible()) {
            clearAbility();
            console.setZIndex(ui.getChildren().size);
        } else {
            updateAbility();
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        ui.pressUp(keycode);
    
        Button pressedButton = ui.buttonPressed(keycode);
        if (pressedButton != null) {
            pressedButton.fire(eventTouchUp);
        }

        return super.keyUp(keycode);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (dialogOnlyInput) {
            return dialogInput(keycode);
        }
        ui.pressDown(keycode);
        if (keycode == Input.Keys.F9 || keycode == Input.Keys.F10) {
            toggleConsole();
            return true;
        }
        if (KeyBinding.Back.isPressed(keycode)) {
            if (console.isVisible()) {
                toggleConsole();
                return true;
            }
        }
        if (console.isVisible())
            return true;
        Button pressedButton = ui.buttonPressed(keycode);
        if (pressedButton != null) {
            pressedButton.fire(eventTouchDown);
        }
        return super.keyDown(keycode);
    }

    private boolean dialogInput(int keycode) {
        if (dialogOnlyInput) {
            if (KeyBinding.Up.isPressed(keycode)) {
                selectPreviousDialogButton();
            }
            if (KeyBinding.Down.isPressed(keycode)) {
                selectNextDialogButton();
            }
            if (KeyBinding.Use.isPressed(keycode)) {
                performTouch(this.getKeyboardFocus());
            }
        }
        return true;
    }

    private void performTouch(Actor actor) {
        if (actor == null)
            return;
        actor.fire(eventTouchDown);
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                actor.fire(eventTouchUp);
            }
        }, 0.10f);
    }

    public void setDebug(boolean b) {
        debugMap = b;
    }

    public boolean isDebugMap() {
        return debugMap;
    }

    public void playerIdle() {
        if (MapStage.getInstance().isInMap()) {
            MapStage.getInstance().startPause(1f);
            MapStage.getInstance().getPlayerSprite().stop();
        } else {
            WorldStage.getInstance().startPause(1f);
            WorldStage.getInstance().getPlayerSprite().stop();
        }
    }

    private void showDialog() {
        playerIdle();
        dialogButtonMap.clear();
        for (int i = 0; i < dialog.getButtonTable().getCells().size; i++) {
            dialogButtonMap.add((TextraButton) dialog.getButtonTable().getCells().get(i).getActor());
        }
        dialog.show(this, Actions.show());
        dialog.setPosition((this.getWidth() - dialog.getWidth()) / 2, (this.getHeight() - dialog.getHeight()) / 2);
        dialogOnlyInput = true;
        gameStage.hudIsShowingDialog(true);
        MapStage.getInstance().hudIsShowingDialog(true);
        if (Forge.hasExternalInput() && !dialogButtonMap.isEmpty())
            this.setKeyboardFocus(dialogButtonMap.first());
    }

    private void hideDialog(boolean exitDungeon) {
        //move exit dungeon code here to ensure the hide animation is finished before exiting to worldmap
        dialog.hide(Actions.sequence(Actions.sizeTo(dialog.getOriginX(), dialog.getOriginY(), 0.3f), Actions.hide(), new Action() {
            @Override
            public boolean act(float v) {
                if (exitDungeon) {
                    MapStage.getInstance().exitDungeon(false);
                    setDisabled(exitToWorldMapActor, true, "[%120][+ExitToWorldMap]", "\u2613");
                    setDisabled(bookmarkActor, true, "[%120][+Bookmark]", "\u2613");
                }
                return true;
            }
        }));

        dialogOnlyInput = false;
        gameStage.hudIsShowingDialog(false);
        MapStage.getInstance().hudIsShowingDialog(false);
    }

    private void selectNextDialogButton() {
        if (dialogButtonMap.size < 2)
            return;
        if (!(this.getKeyboardFocus() instanceof Button)) {
            this.setKeyboardFocus(dialogButtonMap.first());
            return;
        }
        for (int i = 0; i < dialogButtonMap.size; i++) {
            if (this.getKeyboardFocus() == dialogButtonMap.get(i)) {
                i += 1;
                i %= dialogButtonMap.size;
                this.setKeyboardFocus(dialogButtonMap.get(i));
                return;
            }
        }
    }

    private void selectPreviousDialogButton() {
        if (dialogButtonMap.size < 2)
            return;
        if (!(this.getKeyboardFocus() instanceof Button)) {
            this.setKeyboardFocus(dialogButtonMap.first());
            return;
        }
        for (int i = 0; i < dialogButtonMap.size; i++) {
            if (this.getKeyboardFocus() == dialogButtonMap.get(i)) {
                i -= 1;
                if (i < 0)
                    i = dialogButtonMap.size - 1;
                this.setKeyboardFocus(dialogButtonMap.get(i));
                return;
            }
        }
    }

    class ConsoleToggleListener extends ActorGestureListener {
        public ConsoleToggleListener() {
            getGestureDetector().setLongPressSeconds(0.6f);
        }

        @Override
        public boolean longPress(Actor actor, float x, float y) {
            if (GuiBase.isAndroid())
                toggleConsole();
            return super.longPress(actor, x, y);
        }

        @Override
        public void tap(InputEvent event, float x, float y, int count, int button) {
            if (console.isVisible())
                return;
            if (count > 1 && button == 0)
                showHideHUD(!hidden);
            else if (button == 0)
                setHUDOpacity(!transluscent);
            super.tap(event, x, y, count, button);
        }
    }

    public void updateMusic() {
        switch (GameScene.instance().getAdventurePlayerLocation(false, false)) {
            case "green":
                changeBGM(MusicPlaylist.GREEN);
                break;
            case "red":
                changeBGM(MusicPlaylist.RED);
                break;
            case "blue":
                changeBGM(MusicPlaylist.BLUE);
                break;
            case "black":
                changeBGM(MusicPlaylist.BLACK);
                break;
            case "white":
                changeBGM(MusicPlaylist.WHITE);
                break;
            case "waste":
                changeBGM(MusicPlaylist.COLORLESS);
                break;
            default:
                break;
        }
    }

    void changeBGM(MusicPlaylist playlist) {
        if (!audioIsPlaying() && !playlist.equals(SoundSystem.instance.getCurrentPlaylist())) {
            SoundSystem.instance.setBackgroundMusic(playlist);
        }
    }

    void flicker(CharacterSprite sprite) {
        if (sprite.getCollisionHeight() == 0f) {
            SequenceAction flicker = new SequenceAction(Actions.fadeOut(0.25f), Actions.fadeIn(0.25f), Actions.fadeOut(0.25f), Actions.fadeIn(0.25f), new Action() {
                @Override
                public boolean act(float v) {
                    Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {
                            sprite.resetCollisionHeight();
                        }
                    }, 0.5f);
                    return true;
                }
            });
            sprite.addAction(flicker);
        }
    }

    void restorePlayerCollision() {
        flicker(MapStage.getInstance().getPlayerSprite());
        flicker(WorldStage.getInstance().getPlayerSprite());
    }

    public void addNotification(String text) {
        Action preconfigureNotification = new Action() {
            @Override
            public boolean act(float delta) {
                notificationText.setWrap(false);
                notificationText.setText(text);
                notificationText.setColor(Color.BLACK);
                notificationText.setWidth(Math.min(notificationText.getPrefWidth(), Forge.isLandscapeMode() ? getWidth() * 0.25f : getWidth() - 25));
                notificationText.setWrap(true);
                notificationText.layout();

                notificationPane.setSize(notificationText.getWidth() + 10, notificationText.getPrefHeight() + 20);

                notificationPane.setPosition(5, Forge.isLandscapeMode() ? -notificationPane.getHeight() : getHeight());

                notificationPane.getColor().a = 1f;
                notificationPane.layout();
                notificationText.layout();
                return true;
            }
        };

        Action newNotification;

        if (Forge.isLandscapeMode()) {
            newNotification = Actions.after(Actions.sequence(preconfigureNotification,
                    Actions.moveTo(5, 0, 2f),
                    Actions.delay(10f),
                    Actions.alpha(0f, 3f),
                    Actions.sizeTo(0, 0)));
        } else {
            newNotification = Actions.after(Actions.sequence(preconfigureNotification,
                    Actions.moveToAligned(5, getHeight(), Align.topLeft, 2f),
                    Actions.delay(10f),
                    Actions.alpha(0f, 3f),
                    Actions.sizeTo(0, 0)));
        }

        notificationPane.addAction(newNotification);
    }

    public void clearNotifications() {
        notificationText.setText("");
        notificationPane.setBounds(5, Forge.isLandscapeMode() ? -notificationText.getPrefHeight() : getHeight(), getWidth() * 0.4f, 25);
        notificationPane.setStyle(Controls.getSkin().get("paper", ScrollPane.ScrollPaneStyle.class));
        notificationPane.getColor().a = 0f;
    }
}
