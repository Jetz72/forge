package forge.game.logic;

import forge.StaticData;
import forge.game.IIdentifiable;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.regex.Matcher;

import static forge.game.logic.PlayerReference.PLAYER_REFERENCE_PATTERN;

/**
 * A reference to an object that we expect to exist when a GameLogicTest is resolving.
 */
abstract class TestReference <T extends IIdentifiable> implements ITestReference<T> {

    protected int id = -1;

    /* package */ void setID(int id) {
        assert(this.id == -1);
        this.id = id;
    }

    /* package */ int getID() {
        assert(this.id != -1);
        return this.id;
    }

    @Override
    public boolean refersTo(IIdentifiable o) {
        return this.id == o.getId();
    }

    @Override
    public int getQuantity() {
        return 1; //Only CardReferences have quantities.
    }

    /* package */ static class ReferencePool {
        private StaticData staticData;
        private final Map<String, CardReference> pool = new LinkedHashMap<>();
        private final Map<Integer, PlayerReference> playerPool = new HashMap<>(4);
        private final Map<String, LiveReference<?>> livePool = new HashMap<>();
        private int maxID = 0;

        /* package */ ReferencePool() {}

        public ICardReference getCard(String implicitReference) {
            if(this.livePool.containsKey(implicitReference))
                return this.livePool.get(implicitReference).ensureCard();
            if(LiveReference.LIVE_REFERENCE_PATTERN.matcher(implicitReference).matches())
                throw new GameLogicTestException("Unable to find live reference %s", implicitReference);
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
            if(this.livePool.containsKey(implicitReference))
                return this.livePool.get(implicitReference).ensurePlayer(); //This will probably never happen.
            if(LiveReference.LIVE_REFERENCE_PATTERN.matcher(implicitReference).matches())
                throw new GameLogicTestException("Unable to find live reference %s", implicitReference);
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

        public StackReference getStack(String implicitReference) {
            if(!LiveReference.LIVE_REFERENCE_PATTERN.matcher(implicitReference).matches())
                throw new GameLogicTestException("Cannot use concrete reference '%s' here.", implicitReference); //Need to use a <Label>, assigned via `.label` after something like `.expectTrigger` or `.activate`.
            if(!this.livePool.containsKey(implicitReference))
                throw new GameLogicTestException("Unable to find live reference %s", implicitReference);
            LiveReference<?> out = livePool.get(implicitReference);
            if(out instanceof StackReference s)
                return s;
            throw new GameLogicTestException("Live reference `%s` is not a StackReference.", implicitReference);
        }

        public ITestReference<?> get(String implicitReference) {
            if(LiveReference.LIVE_REFERENCE_PATTERN.matcher(implicitReference).matches()) {
                if(!livePool.containsKey(implicitReference))
                    throw new GameLogicTestException("Unable to find live reference %s", implicitReference);
                return livePool.get(implicitReference);
            }
            if(PlayerReference.RELATIVE_REF_NAMES.contains(implicitReference) || PlayerReference.PLAYER_REFERENCE_PATTERN.matcher(implicitReference).matches())
                return getPlayer(implicitReference);
            else if(CardReference.IMPLICIT_REF_PATTERN.matcher(implicitReference).matches())
                return getCard(implicitReference);
            else
                throw new IllegalArgumentException("Syntax error in reference: " + implicitReference);
        }

        public void putLiveCards(String label, Set<Card> cards) {
            label = wrapLiveLabelText(label);
            if(livePool.containsKey(label)) {
                LiveReference<?> current = livePool.get(label);
                if(!(current instanceof LiveCardReference cardRef))
                    throw new GameLogicTestException("Tried to resolve %s as a card reference, but it was defined as '%s'.", label, current.getClass());
                if(current.resolved == null) {
                    cardRef.setResolved(cards);
                    return;
                }
                if(!cards.equals(current.resolved))
                    throw new GameLogicTestException("Tried to resolve %s twice with different data. Original: %s; New: %s", label, current.resolved, cards);
                return;
            }
            assert(false); //Should be initialized already.
            LiveCardReference ref = new LiveCardReference(label);
            ref.setResolved(cards);
            livePool.put(label, ref);
        }

        public void putLiveStack(String label, Set<SpellAbility> stack) {
            label = wrapLiveLabelText(label);
            if(livePool.containsKey(label)) {
                LiveReference<?> current = livePool.get(label);
                if(!(current instanceof StackReference stackRef))
                    throw new GameLogicTestException("Tried to resolve %s as a stack reference, but it was defined as '%s'.", label, current.getClass());
                if(current.resolved == null) {
                    stackRef.setResolved(stack);
                    return;
                }
                if(!stack.equals(current.resolved))
                    throw new GameLogicTestException("Tried to resolve %s twice with different data. Original: %s; New: %s", label, current.resolved, stack);
                return;
            }
            assert(false); //Should be initialized already.
            StackReference ref = new StackReference(label);
            ref.setResolved(stack);
            livePool.put(label, ref);
        }

        /* package */ void initLiveCards(String label) {
            label = wrapLiveLabelText(label);
            livePool.put(label, new LiveCardReference(label));
        }
        /* package */ void initLiveStack(String label) {
            label = wrapLiveLabelText(label);
            livePool.put(label, new StackReference(label));
        }

        private String wrapLiveLabelText(String label) {
            if(!LiveReference.LIVE_REFERENCE_PATTERN.matcher(label).matches())
                return String.format("<%s>", label);
            return label;
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

        /* package */ Collection<CardReference> getConcreteCardReferences() {
            return pool.values();
        }

        /* package */ Collection<PlayerReference> getAllPlayerReferences() {
            return playerPool.values();
        }
    }
}
