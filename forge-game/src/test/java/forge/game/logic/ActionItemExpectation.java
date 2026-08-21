package forge.game.logic;

import forge.game.IIdentifiable;
import forge.game.ability.AbilityKey;
import forge.game.event.GameEvent;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerChangesZone;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Represents a check of the current game state or events occurring before the next Priority or Choice action.
 */
abstract class ActionItemExpectation extends GameLogicTestActionQueue.ActionItem implements GameLogicTestActionQueue.HasImplicitSetup {
    protected final Collection<CardReference> cardRefs;
    protected final String description;
    protected ZoneType inferredCardRefZone = null;
    protected int inferredCardRefOwnerIndex = -1;

    protected ActionItemExpectation(GameLogicTestActionQueue queue, Collection<? extends GameLogicTestReference> refs, String description, Object... descriptionFormatParams) {
        super(queue);
        this.cardRefs = refs.stream().filter(CardReference.class::isInstance).map(CardReference.class::cast).toList();
        this.description = String.format(description, GameLogicTestException.processFormatParams(descriptionFormatParams));
    }

    @Override
    public Set<CardReference> getCardRefs() {
        return Set.copyOf(cardRefs);
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
        for(CardReference ref : getCardRefs()) {
            if(inferredCardRefZone != null)
                ref.setInferredZone(inferredCardRefZone);
            if(inferredCardRefOwnerIndex != -1)
                ref.setInferredOwner(inferredCardRefOwnerIndex);
        }
    }

    protected static class EventConsumer<T extends GameEvent> {
        protected final Class<T> eventClass;
        protected final Predicate<T> matcher;
        protected final Predicate<T> typeMatcher;
        protected final String stepDescription;
        protected int quantity;
        protected int received;

        EventConsumer(Class<T> eventClass, int quantity, Predicate<T> matcher, Predicate<T> typeMatcher, Object stepDescription) {
            this.eventClass = eventClass;
            this.quantity = quantity;
            this.matcher = matcher;
            this.typeMatcher = typeMatcher;
            this.stepDescription = String.valueOf(GameLogicTestException.processFormatParams(stepDescription)[0]);
        }

        /* package */ boolean matchesType(GameEvent ev) {
            return eventClass.isInstance(ev) && (typeMatcher == null || typeMatcher.test(eventClass.cast(ev)));
        }

        /* package */ boolean matchesConditions(GameEvent ev) {
            return matcher.test(eventClass.cast(ev));
        }

        @Override
        public String toString() {
            if(quantity == 1)
                return (received >= 1 ? "[✓] " : "[ ]") + this.stepDescription;
            else
                return String.format("(%d/%d) %s", received, quantity, stepDescription);
        }
    }

    static class ExpectSingle<T extends GameEvent> extends ActionItemExpectation {
        protected final Class<T> eventType;
        protected final Predicate<T> predicate;

        public ExpectSingle(GameLogicTestActionQueue queue, Collection<? extends GameLogicTestReference> refs, Class<T> eventType, Predicate<T> predicate, String description, Object... descriptionFormatParams) {
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

    private static abstract class ExpectMulti <T extends GameEvent, U extends GameLogicTestReference> extends ActionItemExpectation {
        protected final Class<T> eventType;
        protected final BiPredicate<T, U> predicate;
        protected final List<EventConsumer<T>> allSteps;

        public ExpectMulti(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, Predicate<T> typePredicate, String description, Object... descriptionFormatParams) {
            super(queue, refs, description, descriptionFormatParams);
            this.eventType = eventType;
            this.predicate = predicate;
            this.allSteps = new ArrayList<>(refs.size());
            for(U ref : refs) {
                allSteps.add(new EventConsumer<>(eventType, ref.getQuantity(), event -> predicate.test(event, ref), typePredicate, ref));
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
            for(EventConsumer<T> c : this.allSteps)
                sb.append("\n - ").append(c.toString());
            return sb.toString();
        }
    }

    static class ExpectMultiOrdered <T extends GameEvent, U extends GameLogicTestReference> extends ExpectMulti<T, U> {
        protected final Deque<EventConsumer<T>> remainingSteps;

        public ExpectMultiOrdered(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, String description, Object... descriptionFormatParams) {
            this(queue, refs, eventType, predicate, null, description, descriptionFormatParams);
        }

        public ExpectMultiOrdered(GameLogicTestActionQueue queue, Collection<U> refs, Class<T> eventType, BiPredicate<T, U> predicate, Predicate<T> typePredicate, String description, Object... descriptionFormatParams) {
            super(queue, refs, eventType, predicate, typePredicate, description, descriptionFormatParams);
            this.remainingSteps = new ArrayDeque<>(allSteps);
        }

        @Override
        ConsumeResult receiveEvent(GameEvent event) {
            EventConsumer<T> head = remainingSteps.peek();
            assert(head != null);
            if(!head.matchesType(event))
                return ConsumeResult.MISS;
            else if(!head.matchesConditions(event))
                return ConsumeResult.NEAR_MISS;
            if(++head.received >= head.quantity)
                remainingSteps.poll();
            return remainingSteps.isEmpty() ? ConsumeResult.RESOLVED : ConsumeResult.CONSUMED;
        }
    }

    static class ExpectMultiUnordered <T extends GameEvent, U extends GameLogicTestReference> extends ExpectMulti<T, U> {
        protected final List<EventConsumer<T>> remainingSteps;

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
            for(EventConsumer<T> head : remainingSteps) {
                if (!head.matchesType(event))
                    continue;
                else if (!head.matchesConditions(event)) {
                    foundType = true;
                    continue;
                }
                if (++head.received >= head.quantity)
                    remainingSteps.remove(head);
                return remainingSteps.isEmpty() ? ConsumeResult.RESOLVED : ConsumeResult.CONSUMED;
            }
            return foundType ? ConsumeResult.NEAR_MISS : ConsumeResult.MISS;
        }
    }

    static class ExpectTrigger extends ExpectMultiOrdered<TestGameEvent.Trigger, CardReference> implements GameLogicTestActionQueue.HasFocusAdjustment, GameLogicTestActionQueue.HasImplicitSetup {
        protected final String triggerRef;
        protected final Integer triggerIndex;
        protected final Class<? extends Trigger> apiType;

        public ExpectTrigger(GameLogicTestActionQueue queue, Collection<CardReference> cardRefs, Class<? extends Trigger> apiType, Map<AbilityKey, Object> triggeringObjects, int triggerCount) {
            super(queue, cardRefs, TestGameEvent.Trigger.class, getPredicate(triggeringObjects, apiType, null), "Expect trigger from %s", cardRefs);
            this.allSteps.forEach(step -> step.quantity *= triggerCount);
            this.triggerRef = null;
            this.triggerIndex = null;
            this.apiType = apiType;
        }

        public ExpectTrigger(GameLogicTestActionQueue queue, CardReference cardRef, Integer triggerIndex, Map<AbilityKey, Object> triggeringObjects, String triggerRef) {
            super(queue, Set.of(cardRef), TestGameEvent.Trigger.class, getPredicate(triggeringObjects, null, triggerIndex), "Expect trigger from %s", cardRef);
            assert(cardRef.quantity == 1 || triggerRef == null); //Can't assign a label to multiple triggers.
            this.triggerRef = triggerRef;
            this.triggerIndex = triggerIndex;
            this.apiType = null;
        }

        static BiPredicate<TestGameEvent.Trigger, CardReference> getPredicate(Map<AbilityKey, Object> triggeringObjects, Class<? extends Trigger> apiType, Integer triggerIndex) {
            BiPredicate<TestGameEvent.Trigger, CardReference> out = (triggerEvent, ref) -> ref.refersTo(triggerEvent.trigger.getHostCard());
            if(triggerIndex != null)
                out = out.and((triggerEvent, ref) -> triggerEvent.triggerIndex == triggerIndex);
            if(apiType != null)
                out = out.and((triggerEvent, ref) -> apiType.isInstance(triggerEvent.trigger));
            if(triggeringObjects != null)
                out = out.and((triggerEvent, ref) -> {
                    for(Map.Entry<AbilityKey, Object> e : triggeringObjects.entrySet()) {
                    if(triggerEvent.triggeringObjects.containsKey(e.getKey())) {
                        Object value = triggerEvent.triggeringObjects.get(e.getKey());
                        if(e.getValue() instanceof GameLogicTestReference r && value instanceof IIdentifiable i && r.refersTo(i))
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
            if(this.triggerRef != null && out == ConsumeResult.RESOLVED) {
                queue.putStackLabel(this.triggerRef, ((TestGameEvent.Trigger) event).spellAbility.getId());
            }
            return out;
        }

        @Override
        public void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility) {
            super.doImplicitSetup(lastPriority, focusSpellAbility);
            for(CardReference cardRef : this.cardRefs) {
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
            if(cardRefs.size() > 1)
                return null;
            if(this.apiType != null)
                t = GameLogicTestUtils.getTrigger(cardRefs.iterator().next().getCard(), apiType);
            else if(this.triggerIndex != null)
                t = GameLogicTestUtils.getTrigger(cardRefs.iterator().next().getCard(), triggerIndex);
            else
                return null;
            if(t == null)
                return null;
            return t.getOverridingAbility();
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
