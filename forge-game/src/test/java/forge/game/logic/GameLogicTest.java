package forge.game.logic;

import forge.CardStorageReader;
import forge.LobbyPlayer;
import forge.StaticData;
import forge.card.CardType;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityKey;
import forge.game.card.CardUtil;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.game.trigger.TriggerType;
import forge.util.FileSection;
import forge.util.FileUtil;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeSuite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class GameLogicTest implements GameLogicTestActionQueue.ActionQueueProxy, IHookable {
    public static final Path DIR_RES = Paths.get(Files.isDirectory(Paths.get("./res")) ? "./" : "../forge-gui/", "res");
    public static final Path DIR_CARDS = DIR_RES.resolve("cardsfolder");
    public static final Path DIR_TOKENS = DIR_RES.resolve("tokenscripts");
    public static final Path DIR_EDITIONS = DIR_RES.resolve("editions");
    public static final Path DIR_BLOCKS = DIR_RES.resolve("blockdata");
    public static final Path DIR_LANGUAGES = DIR_RES.resolve("languages");
    public static final Path DIR_LISTS = DIR_RES.resolve("lists");
    public static final Path FILE_KEYWORDS = DIR_LISTS.resolve("NonStackingKWList.txt");
    public static final Path FILE_TYPES = DIR_LISTS.resolve("TypeLists.txt");
    protected static StaticData staticData;
    protected GameLogicTestActionQueue queue;
    protected GameLogicTestSetup setup;
    protected PlayerProxy user, opponent;
    private List<PlayerProxy> players;
    private CardReference.ReferencePool referencePool;

    protected GameLogicTest() {
        this.referencePool = new CardReference.ReferencePool();
        this.queue = new GameLogicTestActionQueue(this.referencePool);
        this.setup = new GameLogicTestSetup(this.referencePool);
        this.user = new PlayerProxy(0);
        this.opponent = new PlayerProxy(1);
        this.players = new ArrayList<>();
        this.players.add(this.user);
        this.players.add(this.opponent);
    }

    protected void resetState() {
        this.referencePool = new CardReference.ReferencePool();
        this.queue = new GameLogicTestActionQueue(this.referencePool);
        this.setup = new GameLogicTestSetup(this.referencePool);
        this.user = new PlayerProxy(0);
        this.opponent = new PlayerProxy(1);
        this.players = new ArrayList<>();
        this.players.add(this.user);
        this.players.add(this.opponent);
    }

    @Override
    public GameLogicTestActionQueue getQueue() {
        return this.queue;
    }

    @Override
    public CardReference.ReferencePool getReferencePool() {
        return this.referencePool;
    }

    private static boolean keywordsLoaded = false;
    @BeforeSuite
    protected static void initStaticData() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", DIR_LANGUAGES.toString());
        staticData = new StaticData(
                new CardStorageReader(DIR_CARDS.toString(), null, true),
                new CardStorageReader(DIR_TOKENS.toString(), null, false), //TODO: Support lazy loading of tokens
                null, null,
                DIR_EDITIONS.toString(),
                null, //No custom cards
                DIR_BLOCKS.toString(), //TODO: Make StaticData able to work with no art or edition data.
                "",
                "Latest Art All Editions",
                true, true, true, false
        );

        //TODO: Below was just copied out of FModel.loadDynamicGamedata - need to find a common home for it.
        if (!CardType.Constant.LOADED.isSet()) {

            final Map<String, List<String>> contents = FileSection.parseSections(FileUtil.readFile(FILE_TYPES.toString()));

            for (String sectionName: contents.keySet()) {
                CardType.Helper.parseTypes(sectionName, contents.get(sectionName));
            }

            CardType.Constant.LOADED.set();
        }

        if (!keywordsLoaded) {
            final List<String> nskwListFile = FileUtil.readFile(FILE_KEYWORDS.toString());

            if (nskwListFile.size() > 1) {
                for (final String s : nskwListFile) {
                    if (s.length() > 1) {
                        CardUtil.NON_STACKING_LIST.add(s);
                    }
                }
            }
            keywordsLoaded = true;
        }
    }

    protected Game buildGame() {
        GameRules rules = this.setup.getGameRules();
        List<RegisteredPlayer> registeredPlayers = new ArrayList<>(players.size());
        for(PlayerProxy player : this.players) {
            RegisteredPlayer registeredPlayer = new RegisteredPlayer(new Deck(player.getName()));
            registeredPlayer.setPlayer(player);
            registeredPlayer.setId(player.setup.getID());
            registeredPlayers.add(registeredPlayer);
        }
        Match match = new Match(rules, registeredPlayers, "Test Match");
        Game game = match.createGame();

        Trigger.resetIDs();
        TriggerHandler trigHandler = game.getTriggerHandler();
        trigHandler.clearDelayedTrigger();

        game.setAge(GameStage.Play);
        game.getTriggerHandler().runTrigger(TriggerType.NewGame, AbilityKey.newMap(), true);

        return game;
    }

    protected void onGameStart(Game game) {
        this.setup.applyToGame(game);
        game.subscribeToEvents(this.queue);
    }

    @Override
    public void run(IHookCallBack callBack, ITestResult testResult) {
        callBack.runTestMethod(testResult);
        try {
            if (testResult.getThrowable() == null)
                this.resolveAndExecuteQueue();
            else
                testResult.setStatus(ITestResult.FAILURE);
        } catch (RuntimeException e) {
            testResult.setThrowable(e);
            testResult.setStatus(ITestResult.FAILURE);
        } finally {
            testResult.setEndMillis(System.currentTimeMillis());
            //Cleanup.
            this.resetState();
        }
    }

    protected void resolveAndExecuteQueue() {
        //Step I: Load the needed cards.
        this.referencePool.supplyStaticData(staticData);

        //Step II: Resolve implicit setup.
        //TODO: Create proxies for newly referenced players
        this.setup.initFromQueue(this.queue);

        //Step III: Build the game state.
        Game game = this.buildGame();

        //Step IV: Run the game, player controllers and queue will handle the test execution.
        queue.queueState = GameLogicTestActionQueue.ActionQueueState.RUNNING;

        queue.log("-Start of test-");
        Thread thread = Thread.currentThread();
        String threadName = thread.getName();
        thread.setName("Game-Test-" + threadName); //Running the game directly on this thread, so ensure ThreadUtil.isGameThread recognizes it as such.
        try {
            game.getPhaseHandler().startFirstTurn(game.getPlayers().get(0), () -> this.onGameStart(game));
            System.out.println("Test Passed");
            System.out.println("===============================================\nInferred Setup State:\n" + this.setup.toString());
            System.out.println("===============================================\nTest Log:");
            for(String log : queue.logBuffer)
                System.out.println(log);
        }
        catch (RuntimeException | AssertionError e) {
            System.err.println("Test Failed - " + e);
            System.err.println("===============================================\nSetup State:\n" + this.setup.toString());
            System.err.println("===============================================\nTest Log:");
            for(String log : queue.logBuffer)
                System.err.println(log);
            System.err.println(e.getMessage());
            GameState finalState = new GameLogicTestSetup(referencePool);
            finalState.initFromGame(game);
            System.err.println("===============================================\nFinal State:\n" + finalState);
            throw e;
        }
        finally {
            thread.setName(threadName); //If the thread gets reused by TestNG, ensure it doesn't accumulate prefixes.
        }
    }

    protected class PlayerProxy extends LobbyPlayer implements GameLogicTestActionQueue.ActionQueueProxy, IGameEntitiesFactory {
        public final int playerIndex;
        public final GameLogicTestSetup.PlayerSetup setup;
        public final int id;

        private PlayerProxy(int playerIndex) {
            super("Player " + (playerIndex + 1));
            this.playerIndex = playerIndex;
            this.setup = GameLogicTest.this.setup.getPlayerState(this.playerIndex);
            this.id = GameLogicTest.this.referencePool.getPlayer(playerIndex).id;
        }

        @Override
        public void hear(LobbyPlayer player, String message) {} //no-op

        @Override
        public GameLogicTestActionQueue getQueue() {
            return GameLogicTest.this.queue;
        }

        @Override
        public CardReference.ReferencePool getReferencePool() {
            return GameLogicTest.this.referencePool;
        }

        @Override
        public int getPlayerIndexOverride() {
            return this.playerIndex;
        }

        @Override
        public PlayerController createMindSlaveController(Player master, Player slave) {
            return null; //TODO
        }

        @Override
        public Player createIngamePlayer(Game game, int id) {
            assert(this.id == id);
            Player player = new Player(this.name, game, id);
            GameLogicTestPlayerController playerController = new GameLogicTestPlayerController(game, player, this, setup.index, GameLogicTest.this.queue);
            player.setFirstController(playerController);
            return player;
        }
    }


}
