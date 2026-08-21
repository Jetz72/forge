package forge.game.logic;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.eventbus.Subscribe;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.event.*;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

//TODO: Fix the visibility across Action Items.
public class GameLogicTestActionQueue {

    enum ActionQueueState {
        BUILDING,
        INITIALIZING,
        RUNNING
    }

    /* package */ ActionQueueState queueState = ActionQueueState.BUILDING;

    final ArrayDeque<ActionItem> queue = new ArrayDeque<>();
    final CardReference.ReferencePool referencePool;

    private int workingPlayerIndex = -1;
    private CardReference targetingFor = null;

    private ActionItemPriority activePriorityItem;
    /** All remaining criteria (non-priority) nodes between the active priority item and the next priority item. */
    private final ArrayDeque<ActionItem> unhandledCriteriaItems = new ArrayDeque<>();
    /** All remaining criteria nodes up until the next `.then()` node. */
    private final List<ActionItem> criteriaBlock = new ArrayList<>();
    /** Number of `.then()` nodes since the last priority node. */
    private int thenCount = 0;
    private int lastActedTurn = 0;

    public final List<String> logBuffer = new ArrayList<>();
    private final ListMultimap<Class<? extends GameEvent>, String> expectNearMiss = ArrayListMultimap.create();
    private final Set<ActionItemExpectation> partiallyResolvedEvents = new HashSet<>();

    private final Map<String, Integer> stackLabelIDs = new TreeMap<>();

    /* package */ GameLogicTestActionQueue(CardReference.ReferencePool referencePool) {
        this.referencePool = referencePool;
    }

    //Stuff for building the queue.

    /* package */ void push(ActionItem item) {
        queue.addLast(item);
        this.workingPlayerIndex = -1;
        if (item instanceof ActionItemPriority priority)
            this.targetingFor = priority.subject;
    }

    /* package */ void then() {
        if(!queue.isEmpty() && !(queue.peekLast() instanceof QueueInterrupt))
            queue.addLast(new QueueInterrupt(this));
        this.targetingFor = null;
    }

    /* package */ void setWorkingPlayerIndex(int workingPlayerIndex) {
        this.workingPlayerIndex = workingPlayerIndex;
    }

    /* package */ int getWorkingPlayerIndex() {
        if(workingPlayerIndex >= 0)
            return this.workingPlayerIndex;
        if(queue.isEmpty())
            return 0;
        return queue.peekLast().getPlayerIndex();
    }

    /* package */ void setTargetingFor(CardReference targetingFor) {
        this.targetingFor = targetingFor;
    }

    /* package */ CardReference getTargetingFor() {
        return targetingFor;
    }


    //Labels - used for finding stack items in a game.

    /* package */ void putStackLabel(String label, int id) {
        assert(!this.stackLabelIDs.containsKey(label));
        this.stackLabelIDs.put(label, id);
    }

    /* package */ SpellAbilityStackInstance getStackItemFromLabel(Game game, String label) {
        int id = this.stackLabelIDs.get(label);
        return game.getStack().stream().filter(i -> i.getSpellAbility().getId() == id).findFirst().orElseThrow(() -> new GameLogicTestException("Unable to find a spell or ability on the stack with label '%s'", label));
    }


    //Stuff for actually running the queue.

    /* package */ void advancePriority(Game game) {
        this.runAsserts(game, true);
        assertNoPendingCriteriaItems();
        this.thenCount = 0;
        this.lastActedTurn = game.getPhaseHandler().getTurn();
        if(queue.isEmpty()) {
            //We're done.
            this.activePriorityItem = null;
            return;
        }
        assert(queue.peekFirst() instanceof ActionItemPriority);
        this.activePriorityItem = (ActionItemPriority) queue.pollFirst();
        while(!queue.isEmpty() && !(queue.peekFirst() instanceof ActionItemPriority)) {
            unhandledCriteriaItems.push(queue.pollFirst());
        }

        this.advanceCriteriaBlock();
    }

    /* package */ ActionItemPriority peekPriority() {
        if(queue.isEmpty())
            return null;
        return (ActionItemPriority) queue.peekFirst();
    }

    /* package */ ActionItemPriority currentPriority() {
        return this.activePriorityItem;
    }

    /* package */ void runAsserts(Game game, boolean requirePass) {
        do {
            try {
                criteriaBlock.stream()
                        .filter(ActionItemAssertion.class::isInstance)
                        .map(ActionItemAssertion.class::cast)
                        .forEach(a -> a.doAssert(game));
            }
            catch (GameLogicTestException e) {
                if(requirePass)
                    throw e;
                return;
            }
            //If we made it here, this batch of assertions passed.
            criteriaBlock.removeIf(ActionItemAssertion.class::isInstance);
        } while(advanceCriteriaBlock());
    }

    /* package */ void assertNoTimeout(Game game) {
        if(!game.getStack().isEmpty())
            return;
        int currentTurn = game.getPhaseHandler().getTurn();
        if(currentTurn - this.lastActedTurn > game.getPlayers().size()) {
            ActionItemPriority next = peekPriority();
            assert(next != null);
            throw new GameLogicTestException("Failed to proceed to priority item after %d turns: ", game.getPlayers().size(), next);
        }
    }

    /* package */ void assertNoPartiallyFinishedEvents() {
        if(partiallyResolvedEvents.isEmpty())
            return;
        StringBuilder sb = new StringBuilder("Unresolved criteria between ");
        ActionItemPriority nextPriorityItem = this.peekPriority();
        sb.append(activePriorityItem == null ? "start of test" : activePriorityItem).append(" and ");
        sb.append(nextPriorityItem == null ? "end of test" : nextPriorityItem);
        if(thenCount > 0)
            sb.append(" after `.then()` #").append(thenCount);
        sb.append(":\n");
        if(partiallyResolvedEvents.size() == 1)
            sb.append("  A series of concurrent events was expected but only partially delivered: ").append(partiallyResolvedEvents.iterator().next().toString());
        else {
            sb.append("  ").append(partiallyResolvedEvents.size()).append(" sets of concurrent events were expected but only partially delivered: ");
            for(ActionItem item : partiallyResolvedEvents)
                sb.append("\n  - ").append(item);
        }
        sb.append('\n');

        Set<Class<? extends GameEvent>> failedEventTypes = partiallyResolvedEvents.stream()
                .flatMap(e -> e.getUnresolvedEventTypes().stream())
                .filter(expectNearMiss::containsKey)
                .collect(Collectors.toSet());

        if(!expectNearMiss.isEmpty() && !failedEventTypes.isEmpty()) {
            List<String> nearMissText = new ArrayList<>(expectNearMiss.size());
            failedEventTypes.stream().flatMap(k -> expectNearMiss.get(k).stream()).forEach(nearMissText::add);
            if(nearMissText.size() == 1)
                sb.append("  An event of a matching type was detected: ").append(nearMissText.get(0));
            else {
                sb.append("  ").append(nearMissText.size()).append(" events of matching types were detected: ");
                for(String s : nearMissText)
                    sb.append("\n  - ").append(s);
            }
            sb.append('\n');
        }

        throw new GameLogicTestException(sb.toString());
    }

    /* package */ <T extends ActionItemChoice> T getPendingChoiceOfType(Class<T> choiceType, int playerIndex) {
        return this.criteriaBlock.stream().filter(c -> c.playerIndex == playerIndex).filter(choiceType::isInstance).map(choiceType::cast).findFirst().orElse(null);
    }

    /* package */ <T extends ActionItemChoice> T getPendingChoiceOfType(Class<T> choiceType, Predicate<T> filter, int playerIndex) {
        return this.criteriaBlock.stream().filter(c -> c.playerIndex == playerIndex).filter(choiceType::isInstance).map(choiceType::cast).filter(filter).findFirst().orElse(null);
    }

    /* package */ <T extends ActionItemChoice> void lookAheadAndFailIfChoiceOfType(Class<T> choiceType, Predicate<T> filter, int playerIndex) {
        if(this.unhandledCriteriaItems.stream().filter(c -> c.playerIndex == playerIndex).filter(choiceType::isInstance).map(choiceType::cast).anyMatch(filter))
            assertNoPendingCriteriaItems();
    }

    /* package */ void fulfillCriteria(ActionItem criteria) {
        assert(criteriaBlock.contains(criteria));
        this.criteriaBlock.remove(criteria);
        this.advanceCriteriaBlock();
    }

    @Subscribe
    public void receiveGameEvent(GameEvent ev) {
        boolean foundType = false;
        for (ActionItem item : criteriaBlock) {
            if(!(item instanceof ActionItemExpectation evItem))
                continue;
            ActionItemExpectation.ConsumeResult result = evItem.receiveEvent(ev);
            if(result == ActionItemExpectation.ConsumeResult.MISS) {
                continue;
            }
            else if(result == ActionItemExpectation.ConsumeResult.NEAR_MISS) {
                foundType = true;
                continue;
            }
            else if(result == ActionItemExpectation.ConsumeResult.CONSUMED) {
                partiallyResolvedEvents.add(evItem);
            }
            this.log("Event accepted: %s", ev);
            if(result == ActionItemExpectation.ConsumeResult.RESOLVED) {
                partiallyResolvedEvents.remove(evItem);
                this.log("Expectation resolved: %s", evItem.getDescription());
                this.fulfillCriteria(evItem);
            }
            return;
        }
        if(foundType) {
            expectNearMiss.put(ev.getClass(), ev.toString());
        }
    }

    /* package */ void log(String log, Object... formatArgs) {
        this.logBuffer.add(String.format(log, GameLogicTestException.processFormatParams(formatArgs)));
    }

    private void assertNoPendingCriteriaItems() {
        if(this.unhandledCriteriaItems.isEmpty() && this.criteriaBlock.isEmpty())
            return;
        assert(!criteriaBlock.isEmpty()); //If this is empty, we failed to call advanceCriteriaBlock.
        StringBuilder sb = new StringBuilder("Unresolved criteria between ");
        ActionItemPriority nextPriorityItem = this.peekPriority();
        sb.append(activePriorityItem == null ? "start of test" : activePriorityItem).append(" and ");
        sb.append(nextPriorityItem == null ? "end of test" : nextPriorityItem);
        if(thenCount > 0)
            sb.append(" after `.then()` #").append(thenCount);
        sb.append(":\n");
        List<ActionItem> failedChoices = new ArrayList<>(), failedExpects = new ArrayList<>(), failedAsserts = new ArrayList<>();
        Set<Class<? extends GameEvent>> failedEventTypes = new HashSet<>();
        for(ActionItem item : criteriaBlock) {
            if(item instanceof ActionItemChoice)
                failedChoices.add(item);
            else if(item instanceof ActionItemExpectation expectation) {
                failedExpects.add(item);
                failedEventTypes.addAll(expectation.getUnresolvedEventTypes());
            }
            else if(item instanceof ActionItemAssertion)
                failedAsserts.add(item);
        }
        assert(!failedChoices.isEmpty() || !failedExpects.isEmpty() || !failedAsserts.isEmpty());
        if(!failedChoices.isEmpty()) {
            if(failedChoices.size() == 1)
                sb.append("  A choice was expected but the player was never prompted: ").append(failedChoices.get(0).toString());
            else {
                sb.append("  ").append(failedChoices.size()).append(" choices were expected but never prompted: ");
                for(ActionItem item : failedChoices)
                    sb.append("\n  - ").append(item);
            }
            sb.append('\n');
        }
        if(!failedExpects.isEmpty()) {
            if(failedExpects.size() == 1)
                sb.append("  An event was expected but never registered: ").append(failedExpects.get(0).toString());
            else {
                sb.append("  ").append(failedExpects.size()).append(" events were expected but never registered: ");
                for(ActionItem item : failedExpects)
                    sb.append("\n  - ").append(item);
            }
            sb.append('\n');
            if(!expectNearMiss.isEmpty() && !failedEventTypes.isEmpty()) {
                List<String> nearMissText = new ArrayList<>(expectNearMiss.size());
                failedEventTypes.stream().flatMap(k -> expectNearMiss.get(k).stream()).forEach(nearMissText::add);
                if(nearMissText.size() == 1)
                    sb.append("  An event of a matching type was detected: ").append(nearMissText.get(0));
                else if(nearMissText.size() > 1) {
                    sb.append("  ").append(nearMissText.size()).append(" events of matching types were detected: ");
                    for(String s : nearMissText)
                        sb.append("\n  - ").append(s);
                }
                sb.append('\n');
            }
        }
        if(!failedAsserts.isEmpty()) {
            //Won't currently happen since assertions fire off separately; could modify them to hold results until here.
            if(failedAsserts.size() == 1)
                sb.append("  An assertion was never satisfied: ").append(failedAsserts.get(0).toString());
            else {
                sb.append("  ").append(failedAsserts.size()).append(" assertions were never concurrently satisfied: ");
                for(ActionItem item : failedAsserts)
                    sb.append("\n  - ").append(item);
            }
            sb.append('\n');
        }

        if(!unhandledCriteriaItems.isEmpty())
            sb.append('(').append(unhandledCriteriaItems.size()).append(" subsequent criteria were not checked)");
        throw new GameLogicTestException(sb.toString());
    }

    private boolean advanceCriteriaBlock() {
        if(!criteriaBlock.isEmpty() || unhandledCriteriaItems.isEmpty())
            return false;
        while(!unhandledCriteriaItems.isEmpty()) {
            ActionItem i = unhandledCriteriaItems.pollLast();
            if(i instanceof QueueInterrupt) {
                this.thenCount++;
                if(criteriaBlock.isEmpty())
                    continue; //Shouldn't happen?
                else
                    break;
            }
            criteriaBlock.add(i);
        }
        return true;
    }

    /* package */ abstract static class ActionItem implements GameLogicTestActionQueue.ActionQueueProxy {
        //Action items can't be defined in terms of actual game objects since they're written before the game begins
        final GameLogicTestActionQueue queue;
        final int playerIndex;

        ActionItem(GameLogicTestActionQueue queue) {
            this.queue = queue;
            this.playerIndex = queue.getWorkingPlayerIndex();
        }

        @Override
        public GameLogicTestActionQueue getQueue() {
            return this.queue;
        }

        @Override
        public CardReference.ReferencePool getReferencePool() {
            return this.queue.referencePool;
        }

        public int getPlayerIndex() {
            return this.playerIndex;
        }

        public int getOpponentIndex() {
            return this.playerIndex == 0 ? 1 : 0;
        }

        abstract Set<CardReference> getCardRefs();

        PlayerReference getActivePlayerRef() {
            return getReferencePool().getPlayer(this.playerIndex);
        }

        protected boolean matchesPlayer(GameLogicTestPlayerController controller) {
            return controller.getPlayer() == this.getActivePlayerRef().findPlayer(controller.getGame());
        }
    }


    private static class QueueInterrupt extends ActionItem {
        QueueInterrupt(GameLogicTestActionQueue queue) { super(queue); }

        @Override
        Set<CardReference> getCardRefs() { return Set.of(); }
    }

    /* package */ interface HasImplicitSetup {
        void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility);
    }

    /* package */ interface HasCostAdjustment {
        Cost adjustEstimatedCost(ActionItemPriority lastPriority, Cost currentEstimate);
    }

    /* package */ interface HasFocusAdjustment {
        SpellAbility getSpellAbility();
    }

    /**
     * Interface that gives easy access to the methods for building out an ActionQueue
     */
    @SuppressWarnings("UnusedReturnValue")
    public interface ActionQueueProxy {
        GameLogicTestActionQueue getQueue();
        CardReference.ReferencePool getReferencePool();

        default int getPlayerIndexOverride() {
            return -1;
        }

        private void applyPlayerIndexOverride() {
            int index = this.getPlayerIndexOverride();
            if(index != -1)
                this.getQueue().setWorkingPlayerIndex(index);
        }

        private CardReference getCardRef(String implicitReference) {
            return getReferencePool().getCard(implicitReference);
        }

        private PlayerReference getPlayerRef(String implicitReference) {
            return getReferencePool().getPlayer(implicitReference);
        }

        private PlayerReference[] getPlayerRefs(String... implicitReferences) {
            return Arrays.stream(implicitReferences).map(this::getPlayerRef).toArray(PlayerReference[]::new);
        }

        private PlayerReference getWorkingPlayerRef() {
            return getReferencePool().getPlayer(getQueue().getWorkingPlayerIndex());
        }

        private GameLogicTestReference getRef(String implicitReference) {
            return getReferencePool().get(implicitReference);
        }

        private CardReference[] getCardRefs(String... implicitReferences) {
            return Arrays.stream(implicitReferences).map(this::getCardRef).toArray(CardReference[]::new);
        }

        private GameLogicTestReference[] getRefs(String... implicitReferences) {
            return Arrays.stream(implicitReferences).map(this::getRef).toArray(GameLogicTestReference[]::new);
        }

        default ActionQueueProxy cast(String cardRef) {
            return priority(ActionItemPriority.ActionType.PLAY, cardRef, 0);
        }

        default ActionQueueProxy respond(String cardRef) {
            return priority(ActionItemPriority.ActionType.RESPOND, cardRef, 0);
        }

        default ActionQueueProxy activate(String cardRef) {
            return priority(ActionItemPriority.ActionType.ACTIVATE, cardRef, 0);
        }

        default ActionQueueProxy activate(String cardRef, int abilityIndex) {
            return priority(ActionItemPriority.ActionType.ACTIVATE, cardRef, abilityIndex);
        }

        default ActionQueueProxy playLand(String cardRef) {
            return priority(ActionItemPriority.ActionType.LAND_DROP, cardRef, 0);
        }

        private ActionQueueProxy priority(ActionItemPriority.ActionType actionType, String cardRef, int saIndex) {
            applyPlayerIndexOverride();
            GameLogicTestActionQueue queue = getQueue();
            CardReference card = getCardRef(cardRef);
            card.assertSingular();
            ActionItem item = new ActionItemPriority(queue, actionType, card, saIndex);
            queue.push(item);
            return item;
        }

        default ActionQueueProxy target(String... objectRefs) {
            applyPlayerIndexOverride();
            GameLogicTestActionQueue queue = getQueue();
            ActionItem item = new ActionItemChoice.Target(queue, getRefs(objectRefs));
            queue.push(item);
            return item;
        }

        default ActionQueueProxy withXValue(int x) {
            applyPlayerIndexOverride();
            GameLogicTestActionQueue queue = getQueue();
            ActionItem item = new ActionItemChoice.XValue(queue, x);
            queue.push(item);
            return item;
        }

        default ActionQueueProxy then() {
            getQueue().then();
            return this;
        }

        default ActionQueueProxy expectDamage(int amount, String... objectRefs) {
            GameLogicTestActionQueue queue = getQueue();
            GameLogicTestReference[] refs = getRefs(objectRefs);
            //Have to split the references up because there are two separate event types.
            List<CardReference> cardRefs = new ArrayList<>(objectRefs.length);
            List<PlayerReference> playerRefs = new ArrayList<>(objectRefs.length);
            for(GameLogicTestReference ref : refs) {
                if(ref instanceof CardReference cardRef) {
                    cardRefs.add(cardRef);
                }
                else if(ref instanceof PlayerReference playerRef)
                    playerRefs.add(playerRef);
                else assert(false);
            }
            ActionItemExpectation item = null;
            if(!cardRefs.isEmpty()) {
                item = new ActionItemExpectation.ExpectMultiUnordered<>(queue, cardRefs, GameEventCardDamaged.class,
                        (ev, ref) -> ref.refersTo(ev.card()) && amount == ev.amount(),
                        "%d damage dealt (card)", amount);
                item.setInferredCardRefZone(ZoneType.Battlefield);
                queue.push(item);
            }
            if(!playerRefs.isEmpty()) {
                item = new ActionItemExpectation.ExpectMultiUnordered<>(queue, playerRefs, GameEventPlayerDamaged.class,
                        (ev, ref) -> ref.refersTo(ev.target()) && amount == ev.amount(),
                        "%d damage dealt (player)", amount);
                queue.push(item);
            }
            assert(item != null); //Called with no references??
            return item;
        }

        default ActionQueueProxy drawing(String... cardRefs) {
            applyPlayerIndexOverride();
            PlayerReference playerRef = getWorkingPlayerRef();
            GameLogicTestActionQueue queue = getQueue();
            List<CardReference> refs = List.of(getCardRefs(cardRefs));
            ActionItemExpectation item = new ActionItemExpectation.ExpectMultiOrdered<>(queue, refs, GameEventCardChangeZone.class,
                    (ev, ref) -> ref.refersTo(ev.card()) && playerRef.refersTo(ev.to().player()),
                    (ev) -> ev.to().zoneType() == ZoneType.Hand && ev.from().zoneType() == ZoneType.Library,
                    "%s drew card", playerRef
            );
            item.setInferredCardRefOwnerIndex(playerRef.playerIndex);
            item.setInferredCardRefZone(ZoneType.Library);
            queue.push(item);
            return item;
        }

        default ActionQueueProxy expectDeath(String... cardRefs) {
            applyPlayerIndexOverride();
            GameLogicTestActionQueue queue = getQueue();
            List<CardReference> refs = List.of(getCardRefs(cardRefs));
            ActionItemExpectation item = new ActionItemExpectation.ExpectMultiUnordered<>(queue, refs, GameEventCardChangeZone.class,
                    (ev, ref) -> ref.refersTo(ev.card()),
                    (ev) -> ev.to().zoneType() == ZoneType.Graveyard && ev.from().zoneType() == ZoneType.Battlefield,
                    "card died"
            );
            item.setInferredCardRefZone(ZoneType.Battlefield);
            queue.push(item);
            return item;
        }

        default ActionQueueProxy expectTrigger(String sourceCardRef) {
            return this.expectTrigger(sourceCardRef, null, null, null);
        }

        default ActionQueueProxy expectTrigger(String sourceCardRef, int triggerIndex) {
            return this.expectTrigger(sourceCardRef, triggerIndex, null, null);
        }

        default ActionQueueProxy expectTriggers(String sourceCardRef, int triggerCount) {
            return this.expectTriggers(new String[]{sourceCardRef}, null, null, triggerCount);
        }
        //Can add variants as needed, supporting quantity, ability indexes, multiple source cards, labels, and/or triggeringObjects.

        private ActionQueueProxy expectTrigger(String sourceCardRef, Integer abilityIndex, Map<AbilityKey, Object> triggeringObjects, String triggerRef) {
            applyPlayerIndexOverride();
            PlayerReference playerRef = getWorkingPlayerRef();
            GameLogicTestActionQueue queue = getQueue();
            CardReference cardRef = getCardRef(sourceCardRef);
            //Can't infer zone here before paperCard is loaded. See ExpectTrigger.doImplicitSetup
            cardRef.setInferredOwner(playerRef.playerIndex);
            ActionItem item = new ActionItemExpectation.ExpectTrigger(queue, cardRef, abilityIndex, triggeringObjects, triggerRef);
            queue.setTargetingFor(cardRef);
            queue.push(item);
            return item;
        }

        private ActionQueueProxy expectTriggers(String[] sourceCardRef, Class<? extends Trigger> apiType, Map<AbilityKey, Object> triggeringObjects, int triggerCount) {
            applyPlayerIndexOverride();
            PlayerReference playerRef = getWorkingPlayerRef();
            GameLogicTestActionQueue queue = getQueue();
            CardReference[] cardRefs = getCardRefs(sourceCardRef);
            for (CardReference r : cardRefs) {
                //Can't infer zone here before paperCard is loaded. See ExpectTrigger.doImplicitSetup
                r.setInferredOwner(playerRef.playerIndex);
            }
            ActionItem item = new ActionItemExpectation.ExpectTrigger(queue, List.of(cardRefs), apiType, triggeringObjects, triggerCount);
            queue.setTargetingFor(cardRefs.length == 1 ? cardRefs[0] : null);
            queue.push(item);
            return item;
        }

        default ActionQueueProxy assertZone(ZoneType zone, String... cardRefs) {
            GameLogicTestActionQueue queue = getQueue();
            ActionItem item = new ActionItemAssertion(queue, getCardRefs(cardRefs)) {
                @Override
                public void doAssert(Game game) {
                    assertForAll(game, c -> c.getZone().getZoneType(), zone, "zone");
                }
            };
            queue.push(item);
            return item;
        }

        default ActionQueueProxy assertPT(int power, int toughness, String... cardRefs) {
            GameLogicTestActionQueue queue = getQueue();
            ActionItem item = new ActionItemAssertion(queue, getCardRefs(cardRefs)) {
                @Override
                public void doAssert(Game game) {
                    assertForAll(game, Card::getNetPower, power, "power");
                    assertForAll(game, Card::getNetToughness, toughness, "toughness");
                }
            };
            queue.push(item);
            return item;
        }

//        default ActionQueueProxy assertLife(int life, String playerRef) {
//            GameLogicTestActionQueue queue = getQueue();
//            ActionItem item = new ActionItemAssertion(queue, getPlayerRef(playerRef)) {
//                @Override
//                public void doAssert(Game game) {
//                    //TODO
//                }
//            };
//            queue.push(item);
//            return item;
//        }
    }
}
