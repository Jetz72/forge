package forge.game.logic;

import forge.StaticData;
import forge.game.GameEntity;
import forge.game.GameEntityView;
import forge.game.GameObject;
import forge.game.IIdentifiable;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.regex.Matcher;

import static forge.game.logic.PlayerReference.PLAYER_REFERENCE_PATTERN;

/**
 * A reference to an object that we expect to exist when a GameLogicTest is resolving.
 */
abstract class GameLogicTestReference {

    protected int id = -1;

    /* package */ CardReference ensureCard() {
        if(this instanceof CardReference c)
            return c;
        throw new IllegalArgumentException("A player reference cannot be used here.");
    }

    /* package */ PlayerReference ensurePlayer() {
        if(this instanceof PlayerReference p)
            return p;
        throw new IllegalArgumentException("A card reference cannot be used here.");
    }

    /* package */ void setID(int id) {
        assert(this.id == -1);
        this.id = id;
    }

    /* package */ int getID() {
        assert(this.id != -1);
        return this.id;
    }

    /* package */ boolean refersTo(IIdentifiable o) {
        return this.id == o.getId();
    }

    /* package */ int getQuantity() {
        return 1; //Only CardReferences have quantities.
    }

    /* package */ void assertSingular() {
        if(this.getQuantity() > 1)
            throw new UnsupportedOperationException("Quantity > 1 is not allowed here.");
    }

    /* package */ static class ReferencePool {
        private StaticData staticData;
        private final Map<String, CardReference> pool = new LinkedHashMap<>();
        private final Map<Integer, PlayerReference> playerPool = new HashMap<>(4);
        private int maxID = 0;

        /* package */ ReferencePool() {}

        public CardReference getCard(String implicitReference) {
            if(this.pool.containsKey(implicitReference))
                return this.pool.get(implicitReference);
            Matcher matcher = CardReference.IMPLICIT_REF_PATTERN.matcher(implicitReference);
            if(!matcher.matches()) {
                if(PlayerReference.RELATIVE_REF_NAMES.contains(implicitReference) || PLAYER_REFERENCE_PATTERN.matcher(implicitReference).matches())
                    throw new IllegalArgumentException("A player reference cannot be used here.");
                throw new IllegalArgumentException("Syntax error in card reference: " + implicitReference);
            }
            CardReference ref = new CardReference(implicitReference, matcher);
            handleNewRef(ref, implicitReference);
            return ref;
        }

        public PlayerReference getPlayer(String implicitReference) {
            int playerIndex;
            if (implicitReference.equals("Self"))
                playerIndex = 0;
            else if (implicitReference.equals("Opponent"))
                playerIndex = 1;
            else {
                Matcher matcher = PLAYER_REFERENCE_PATTERN.matcher(implicitReference);
                if (!matcher.matches()) {
                    if(CardReference.IMPLICIT_REF_PATTERN.matcher(implicitReference).matches())
                        throw new IllegalArgumentException("A card reference cannot be used here.");
                    throw new IllegalArgumentException("Syntax error in player reference: " + implicitReference);
                }
                playerIndex = Integer.parseInt(matcher.group("index")) - 1;
            }
            return this.getPlayer(playerIndex);
        }

        public PlayerReference getPlayer(int playerIndex) {
            if(this.playerPool.containsKey(playerIndex))
                return this.playerPool.get(playerIndex);
            PlayerReference ref = new PlayerReference(playerIndex);
            ref.setID(this.maxID++);
            playerPool.put(playerIndex, ref);
            return ref;
        }

        public GameLogicTestReference get(String implicitReference) {
            if(PlayerReference.RELATIVE_REF_NAMES.contains(implicitReference) || PlayerReference.PLAYER_REFERENCE_PATTERN.matcher(implicitReference).matches())
                return getPlayer(implicitReference);
            else if(CardReference.IMPLICIT_REF_PATTERN.matcher(implicitReference).matches())
                return getCard(implicitReference);
            else
                throw new IllegalArgumentException("Syntax error in reference: " + implicitReference);
        }

        public List<CardReference> loadLands(int playerIndex, Map<String, Integer> landCounts) {
            List<CardReference> out = new ArrayList<>(landCounts.size());
            for (Map.Entry<String, Integer> e : landCounts.entrySet()) {
                int quantity = e.getValue();
                if (quantity <= 0)
                    continue;
                String name = e.getKey();
                String refString = String.format("[Player %d's %dx Basic] %s", (playerIndex + 1), quantity, name);
                CardReference ref = new CardReference(name, playerIndex, quantity, ZoneType.Battlefield, refString);
                handleNewRef(ref, refString);
                out.add(ref);
            }
            return out;
        }

        public CardReference loadDeckPlaceholders(int playerIndex) {
            final int quantity = 10;
            final String name = "Wastes";
            String refString = String.format("[Player %d's %dx Deck-Filler] %s", (playerIndex + 1), quantity, name);
            CardReference ref = new CardReference(name, playerIndex, quantity, ZoneType.Library, refString);
            handleNewRef(ref, refString);
            return ref;
        }

        private void handleNewRef(CardReference ref, String refString) {
            ref.setID(this.maxID);
            this.maxID += ref.quantity;
            pool.put(refString, ref);
            if(this.staticData != null)
                ref.setCard(this.staticData.getOrLoadCommonCard(ref.cardName, null, -1, false));
        }

        /* package */ void supplyStaticData(StaticData staticData) {
            this.staticData = staticData;
            for(CardReference ref : pool.values()) {
                String cardName = ref.cardName;
                ref.setCard(staticData.getOrLoadCommonCard(cardName, null, -1, false));
            }
        }

        /* package */ Collection<CardReference> getAllReferences() {
            return pool.values();
        }

        /* package */ Collection<PlayerReference> getAllPlayerReferences() {
            return playerPool.values();
        }
    }
}
