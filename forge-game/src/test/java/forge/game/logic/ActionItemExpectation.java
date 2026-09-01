package forge.game.logic;

import forge.game.IIdentifiable;
import forge.game.ability.AbilityKey;
import forge.game.event.GameEvent;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a check of the current game state or events occurring before the next Priority or Choice action.
 */
abstract class ActionItemExpectation extends GameLogicTestActionQueue.ActionItem implements GameLogicTestActionQueue.HasImplicitSetup {
    protected final Set<ICardReference> cardRefs;
    protected final String description;
    protected ZoneType inferredCardRefZone = null;
    protected int inferredCardRefOwnerIndex = -1;

    private final Set<CardReference> staticCardRefs;

    protected ActionItemExpectation(GameLogicTestActionQueue queue, Collection<? extends ITestReference<?>> refs, String description, Object... descriptionFormatParams) {
        super(queue);
        this.cardRefs = refs.stream().filter(ICardReference.class::isInstance).map(ICardReference.class::cast).collect(Collectors.toUnmodifiableSet());
        this.staticCardRefs = cardRefs.stream().filter(CardReference.class::isInstance).map(CardReference.class::cast).collect(Collectors.toUnmodifiableSet());
        this.description = String.format(description, GameLogicTestException.processFormatParams(descriptionFormatParams));
    }

    @Override
    public Set<ICardReference> getCardRefs() {
        return this.cardRefs;
    }

    protected Set<CardReference> getStaticCardRefs() {
        return this.staticCardRefs;
    }

    enum ConsumeResult {
        /** Indicates that this expectation node has no interest in this event type. */
        MISS,
        /** Indicates that this expectation node is looking for events of this type, but that this one was unapplicable. */
        NEAR_MISS,
        /** Indicates that the event was received and partially fulfills the expectation of this node. */
        CONSUMED,
        /** Indicates that the event was received and that this node's criteria is now complete. */
        RESOLVED
    }

    abstract /* package */ ConsumeResult receiveEvent(GameEvent event);

    abstract /* package */ Set<Class<? extends GameEvent>> getUnresolvedEventTypes();

    /* package */ void setInferredCardRefZone(ZoneType zone) {
        this.inferredCardRefZone = zone;
    }

    /* package */ void setInferredCardRefOwnerIndex(int ownerIndex) {
        this.inferredCardRefOwnerIndex = ownerIndex;
    }

    @Override
    public void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility) {
        for(CardReference ref : getStaticCardRefs()) {
            if(inferredCardRefZone != null)
                ref.setInferredZone(inferredCardRefZone);
            if(inferredCardRefOwnerIndex != -1)
                ref.setInferredOwner(inferredCardRefOwnerIndex);
        }
    }

    protected static class EventConsumer<T extends GameEvent, U extends ITestReference<?>> {
        protected final Class<T> eventClass;
        protected final U reference;
        protected final BiPredicate<T, U> matcher;
        protected final Predicate<T> typeMatcher;
        protected final String stepDescription;
        protected final int numTimesExpected;
        protected int received;

        EventConsumer(Class<T> eventClass, U reference, BiPredicate<T, U> matcher, Predicate<T> typeMatcher, Object stepDescription, int numTimesExpected) {
            this.eventClass = eventClass;
            this.reference = reference;
            this.matcher = matcher;
            this.typeMatcher = typeMatcher;
            this.stepDescription = String.valueOf(GameLogicTestException.processFormatParams(stepDescription)[0]);
            this.numTimesExpected = numTimesExpected;
        }

        /* package */ boolean matchesType(GameEvent ev) {
            return eventClass.isInstance(ev) && (typeMatcher == null || typeMatcher.test(eventClass.cast(ev)));
        }

        /* package */ boolean matchesConditions(GameEvent ev) {
            return matcher.test(eventClass.cast(ev), reference);
        }

        /* package */ ConsumeResult tryConsume(GameEvent ev) {
            if(!matchesType(ev))
                return ConsumeResult.MISS;
            if(!matchesConditions(ev))
                return ConsumeResult.NEAR_MISS;
            if(++this.received < reference.getQuantity() * this.numTimesExpected)
                return ConsumeResult.CONSUMED;
            return ConsumeResult.RESOLVED;
        }

        @Override
        public String toString() {
            if(reference instanceof LiveReference<?> r && !r.isResolved())
                return "[?] " + this.stepDescription;
            else if(reference.getQuantity() == 1)
                return (received >= 1 ? "[✓] " : "[ ]") + this.stepDescription;
            else
                return String.format("(%d/%d) %s", received, reference.getQuantity(), stepDescription);
        }
    }

    static class ExpectSingle<T extends GameEvent> extends ActionItemExpectation {
        protected final Class<T> eventType;
        protected final Predicate<T> predicate;

        public ExpectSingle(GameLogicTestActionQueue queue, Collection<? extends ITestReference<?>> refs, Class<T> eventType, Predicate<T> predicate, String description, Object... descriptionFormatParams) {
            super(queue, refs, description, descriptionFormatParams);
            this.eventType = eventType;
            this.predicate = predicate;
        }

        @Override
        ConsumeResult receiveEvent(GameEvent event) {
            if(!this.eventType.isInstance(event))
                return ConsumeResult.MISS;
            else if(!this.predicate.test(eventType.cast(event)))
                return ConsumeResult.NEAR_MISS;
            else
                return ConsumeResult.RESOLVED;
        }

        @Override /* package */ Set<Class<? extends GameEvent>> getUnresolvedEventTypes() {
            return Set.of(this.eventType);
        }
    }

    private static abstract class ExpectMulti <T extends GameEvent, U extends ITestReference<?>> extends ActionItemExpectation {
        protected final Class<T> eventType;
        protected final BiPredicate<T, U> predicate;
        protected final List<EventConsumer<T, U>> allSteps;

        public ExpectMulti(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, Predicate<T> typePredicate, String description, Object... descriptionFormatParams) {
            this(queue, refs, 1, eventType, predicate, typePredicate, description, descriptionFormatParams);
        }

        public ExpectMulti(GameLogicTestActionQueue queue, Collection<U> refs, int numTimesExpected, Class<T> eventType, BiPredicate<T, U> predicate, Predicate<T> typePredicate, String description, Object... descriptionFormatParams) {
            super(queue, refs, description, descriptionFormatParams);
            this.eventType = eventType;
            this.predicate = predicate;
            this.allSteps = new ArrayList<>(refs.size());
            for(U ref : refs) {
                allSteps.add(new EventConsumer<>(eventType, ref, predicate, typePredicate, ref, numTimesExpected));
            }
        }

        @Override
        Set<Class<? extends GameEvent>> getUnresolvedEventTypes() {
            //Could simplify this to getEventType, or use it to create an ExpectPoly type that supports multiple event types...
            return Set.of(this.eventType);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            if(this.allSteps.size() == 1) {
                sb.append(" - ").append(this.allSteps.get(0).toString());
                return sb.toString();
            }
            for(EventConsumer<T, U> c : this.allSteps)
                sb.append("\n - ").append(c.toString());
            return sb.toString();
        }
    }

    static class ExpectMultiOrdered <T extends GameEvent, U extends ITestReference<?>> extends ExpectMulti<T, U> {
        protected final Deque<EventConsumer<T, U>> remainingSteps;

        public ExpectMultiOrdered(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, String description, Object... descriptionFormatParams) {
            this(queue, refs, 1, eventType, predicate, null, description, descriptionFormatParams);
        }

        public ExpectMultiOrdered(GameLogicTestActionQueue queue, Collection<U> refs, int numTimesExpected, Class<T> eventType, BiPredicate<T, U> predicate, Predicate<T> typePredicate, String description, Object... descriptionFormatParams) {
            super(queue, refs, numTimesExpected, eventType, predicate, typePredicate, description, descriptionFormatParams);
            this.remainingSteps = new ArrayDeque<>(allSteps);
        }

        @Override
        ConsumeResult receiveEvent(GameEvent event) {
            EventConsumer<T, U> head = remainingSteps.peek();
            assert(head != null);
            ConsumeResult result = head.tryConsume(event);
            if(result == ConsumeResult.RESOLVED) {
                remainingSteps.poll();
                return remainingSteps.isEmpty() ? ConsumeResult.RESOLVED : ConsumeResult.CONSUMED;
            }
            return result;
        }
    }

    static class ExpectMultiUnordered <T extends GameEvent, U extends ITestReference<?>> extends ExpectMulti<T, U> {
        protected final List<EventConsumer<T, U>> remainingSteps;

        public ExpectMultiUnordered(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, String description, Object... descriptionFormatParams) {
            this(queue, refs, eventType, predicate, null, description, descriptionFormatParams);
        }

        public ExpectMultiUnordered(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, Predicate<T> typePredicate, String description, Object... descriptionFormatParams) {
            super(queue, refs, eventType, predicate, typePredicate, description, descriptionFormatParams);
            this.remainingSteps = new ArrayList<>(allSteps);
        }

        @Override
        ConsumeResult receiveEvent(GameEvent event) {
            assert(!remainingSteps.isEmpty());
            boolean foundType = false;
            for(EventConsumer<T, U> head : remainingSteps) {
                ConsumeResult result = head.tryConsume(event);
                if(result == ConsumeResult.NEAR_MISS)
                    foundType = true;
                else if(result == ConsumeResult.CONSUMED)
                    return ConsumeResult.CONSUMED;
                else if(result == ConsumeResult.RESOLVED) {
                    remainingSteps.remove(head);
                    return remainingSteps.isEmpty() ? ConsumeResult.RESOLVED : ConsumeResult.CONSUMED;
                }
            }
            return foundType ? ConsumeResult.NEAR_MISS : ConsumeResult.MISS;
        }
    }

    static class ExpectTrigger extends ExpectMultiOrdered<TestGameEvent.Trigger, ICardReference>
            implements GameLogicTestActionQueue.HasFocusAdjustment, GameLogicTestActionQueue.HasImplicitSetup,
            GameLogicTestActionQueue.ActionQueueProxy_Label
    {
        protected final Integer triggerIndex;
        protected final Class<? extends Trigger> apiType;

        protected Set<SpellAbility> receivedLiveTriggers = null;
        protected String triggerRef = null;

        public ExpectTrigger(GameLogicTestActionQueue queue, Collection<ICardReference> cardRefs, Class<? extends Trigger> apiType, Map<AbilityKey, Object> triggeringObjects, int triggerCount) {
            super(queue, cardRefs, triggerCount, TestGameEvent.Trigger.class, getPredicate(triggeringObjects, apiType, null), null, "Expect trigger from %s", cardRefs);
            this.triggerIndex = null;
            this.apiType = apiType;
        }

        public ExpectTrigger(GameLogicTestActionQueue queue, ICardReference cardRef, Integer triggerIndex, Map<AbilityKey, Object> triggeringObjects) {
            super(queue, Set.of(cardRef), TestGameEvent.Trigger.class, getPredicate(triggeringObjects, null, triggerIndex), "Expect trigger from %s", cardRef);
            this.triggerIndex = triggerIndex;
            this.apiType = null;
        }

        static BiPredicate<TestGameEvent.Trigger, ICardReference> getPredicate(Map<AbilityKey, Object> triggeringObjects, Class<? extends Trigger> apiType, Integer triggerIndex) {
            BiPredicate<TestGameEvent.Trigger, ICardReference> out = (triggerEvent, ref) -> ref.refersTo(triggerEvent.trigger.getHostCard());
            if(triggerIndex != null)
                out = out.and((triggerEvent, ref) -> triggerEvent.triggerIndex == triggerIndex);
            if(apiType != null)
                out = out.and((triggerEvent, ref) -> apiType.isInstance(triggerEvent.trigger));
            if(triggeringObjects != null)
                out = out.and((triggerEvent, ref) -> {
                    for(Map.Entry<AbilityKey, Object> e : triggeringObjects.entrySet()) {
                    if(triggerEvent.triggeringObjects.containsKey(e.getKey())) {
                        Object value = triggerEvent.triggeringObjects.get(e.getKey());
                        if(e.getValue() instanceof ITestReference<?> r && value instanceof IIdentifiable i && r.refersTo(i))
                            continue;
                        if(!Objects.equals(value, e.getValue()))
                            return false;
                    }
                }
                return true;
            });
            return out;
        }

        @Override
        ConsumeResult receiveEvent(GameEvent event) {
            ConsumeResult out = super.receiveEvent(event);
            if (this.triggerRef != null && (out == ConsumeResult.RESOLVED || out == ConsumeResult.CONSUMED)) {
                this.receivedLiveTriggers.add(((TestGameEvent.Trigger) event).spellAbility);
                if (out == ConsumeResult.RESOLVED)
                    queue.referencePool.putLiveStack(this.triggerRef, receivedLiveTriggers);
            }
            return out;
        }

        @Override
        public void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility) {
            super.doImplicitSetup(lastPriority, focusSpellAbility);
            for(CardReference cardRef : this.getStaticCardRefs()) {
                Trigger trigger;
                if(this.triggerIndex != null)
                    trigger = GameLogicTestUtils.getTrigger(cardRef.getCard(), triggerIndex);
                else if(this.apiType != null)
                    trigger = GameLogicTestUtils.getTrigger(cardRef.getCard(), apiType);
                else
                    trigger = GameLogicTestUtils.getTrigger(cardRef.getCard(), 0);
                if(trigger == null)
                    continue;
                cardRef.setInferredZone(trigger.getActiveZone().stream().findFirst().orElse(ZoneType.Battlefield));
            }
        }

        @Override
        void setInferredCardRefZone(ZoneType zone) {
            throw new UnsupportedOperationException("ExpectTrigger infers zone from the trigger");
        }

        @Override
        public SpellAbility getSpellAbility() {
            Trigger t;
            if(cardRefs.size() > 1 || getStaticCardRefs().isEmpty())
                return null;
            if(this.apiType != null)
                t = GameLogicTestUtils.getTrigger(getStaticCardRefs().iterator().next().getCard(), apiType);
            else if(this.triggerIndex != null)
                t = GameLogicTestUtils.getTrigger(getStaticCardRefs().iterator().next().getCard(), triggerIndex);
            else
                return null;
            if(t == null)
                return null;
            return t.getOverridingAbility();
        }

        @Override
        public GameLogicTestActionQueue.ActionQueueProxy label(String label) {
            assert(this.triggerRef == null);
            if(this.allSteps.size() > 1)
                throw new UnsupportedOperationException("Cannot apply a label when listening for multiple triggers!");
            this.receivedLiveTriggers = new HashSet<>();
            this.triggerRef = label;
            queue.referencePool.initLiveStack(label);
            return this;
        }
    }

    @Override
    public String toString() {
        return description;
    }

    public String getDescription() {
        return description;
    }
}
