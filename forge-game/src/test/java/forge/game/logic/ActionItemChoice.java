package forge.game.logic;

import forge.game.Game;
import forge.game.GameObject;
import forge.game.IIdentifiable;
import forge.game.cost.Cost;
import forge.game.cost.CostPartMana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a decision to make while resolving a spell.
 */
abstract class ActionItemChoice <C extends ChoiceConstraints<R>, R> extends GameLogicTestActionQueue.ActionItem {
    protected ActionItemChoice(GameLogicTestActionQueue queue) {
        super(queue);
    }

    abstract R evaluate(Game game, C constraints);

    static class Target extends ActionItemChoice<ChoiceConstraints.Target, Set<GameObject>> implements GameLogicTestActionQueue.HasImplicitSetup {
        final List<ITestReference<?>> targets;
        private final List<ICardReference> cardTargets;
        private final List<PlayerReference> playerTargets;
        private final ICardReference sourceCard;

        Target(GameLogicTestActionQueue queue, ITestReference<?>... targets) {
            super(queue);
            this.targets = List.of(targets);
            this.cardTargets = Arrays.stream(targets)
                    .filter(ICardReference.class::isInstance)
                    .map(ICardReference.class::cast)
                    .collect(Collectors.toList());
            this.playerTargets = Arrays.stream(targets)
                    .filter(PlayerReference.class::isInstance)
                    .map(PlayerReference.class::cast)
                    .collect(Collectors.toList());
            this.sourceCard = queue.getTargetingFor();
            if(sourceCard == null)
                throw new IllegalStateException("Can't assign a target in this context. Need a preceding priority action or expectTrigger.");
            sourceCard.assertSingular("Can't assign a target for a multi-card reference.");
        }

        @Override
        Set<ICardReference> getCardRefs() {
            return Set.copyOf(this.cardTargets);
        }

        @Override
        Set<GameObject> evaluate(Game game, ChoiceConstraints.Target constraints) {
            Set<GameObject> out = new HashSet<>(targets.stream().mapToInt(ITestReference::getQuantity).sum());
            Set<SpellAbility> validSAs = constraints.pool().stream().filter(SpellAbility.class::isInstance).map(SpellAbility.class::cast).collect(Collectors.toSet());
            for(ITestReference<?> r : targets) {
                Set<? extends IIdentifiable> resolved = r.getResolved(game);
                for(IIdentifiable item : resolved) {
                    if(!(item instanceof GameObject i))
                        throw new GameLogicTestException("Illegal type for resolved target %s.", r);
                    if(!constraints.pool().contains(i)) {
                        SpellAbility stackCandidate = validSAs.stream().filter(sa -> i.equals(sa.getHostCard())).findAny().orElse(null);
                        if(stackCandidate != null) {
                            out.add(stackCandidate);
                            validSAs.remove(stackCandidate);
                            continue;
                        }
                        //Otherwise let the containsAll test below catch it.
                    }
                    out.add(i);
                }
            }
            if(!constraints.pool().containsAll(out))
                throw new ChoiceConstraints.ChoiceConstraintException(this, "Invalid targets: %s", out.stream().filter(i -> !constraints.pool().contains(i)).collect(Collectors.toSet()));
            if(out.size() < constraints.spellAbility().getMinTargets())
                throw new ChoiceConstraints.ChoiceConstraintException(this, "Expected >= %d targets (%d provided)", constraints.spellAbility().getMinTargets(), out.size());
            if(out.size() > constraints.spellAbility().getMaxTargets())
                throw new ChoiceConstraints.ChoiceConstraintException(this, "Expected <= %d targets (%d provided)", constraints.spellAbility().getMaxTargets(), out.size());
            //TODO: Other choice constraints. Maybe split a helper method out of InputSelectTargets
            return out;
        }

        /* package */ boolean matchesSpellAbility(SpellAbility ability) {
            if(sourceCard == null)
                return true;
            return sourceCard.refersTo(ability.getHostCard());
        }

        @Override
        public void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility) {
            Set<CardReference> concreteTargets = CardReference.onlyConcreteRefs(this.cardTargets);
            if (focusSpellAbility == null && lastPriority.getSpellAbility() != null) {
                if(concreteTargets.stream().anyMatch(c -> c.zone == null || c.ownerIndex == -1))
                    queue.log("Unable to infer owner or zone for targets of %s. Trying to target non-intrinsic ability?", lastPriority.subject);
                return;
            }
            TargetRestrictions tr = GameLogicTestUtils.findTargetRestrictions(focusSpellAbility);
            if (tr == null)
                throw new UnsupportedOperationException("Tried to assign target for " + focusSpellAbility + ", but the ability does not target.");

            List<ZoneType> zones = tr.getZone();
            ZoneType zone = zones.contains(ZoneType.Battlefield) ? ZoneType.Battlefield : zones.get(0);
            for(CardReference target : concreteTargets)
                target.setInferredZone(zone);

            int targetOwnerIndex = GameLogicTestUtils.targetsEnemyCard(tr) ? getOpponentIndex() : getPlayerIndex();
            for(CardReference target : concreteTargets)
                target.setInferredOwner(targetOwnerIndex);
        }

        @Override
        public String toString() {
            return "Target" + targets;
        }
    }

    static abstract class Value<C extends ChoiceConstraints<R>, R> extends ActionItemChoice<C, R> {
        final R value;

        Value(GameLogicTestActionQueue queue, R value) {
            super(queue);
            this.value = value;
        }

        @Override Set<ICardReference> getCardRefs() { return Set.of(); }

        public R getValue() { return this.value; }

        @Override
        public String toString() {
            return "Value[" + this.value + "]";
        }
    }

    static class Number extends Value<ChoiceConstraints.Range, Integer> {
        Number(GameLogicTestActionQueue queue, Integer value) { super(queue, value); }

        @Override
        Integer evaluate(Game game, ChoiceConstraints.Range constraints) {
            if(this.value == null) //Explicitly cancelling the choice?
                return null;
            if((constraints.min() != null && this.value < constraints.min())
                    || (constraints.max() != null && this.value > constraints.max()))
                throw new ChoiceConstraints.ChoiceConstraintException(this, "value outside of range: %d < %d < %d", constraints.min(), this.value, constraints.max());
            return this.value;
        }
    }

    static class XValue extends Number implements GameLogicTestActionQueue.HasCostAdjustment {
        XValue(GameLogicTestActionQueue queue, Integer value) { super(queue, value); }

        @Override
        public Cost adjustEstimatedCost(ActionItemPriority lastPriority, Cost currentEstimate) {
            SpellAbility focusSpellAbility = lastPriority == null ? null : lastPriority.getSpellAbility();
            if (lastPriority == null || focusSpellAbility == null || !focusSpellAbility.costHasManaX())
                return currentEstimate;

            CostPartMana manaCost = focusSpellAbility.getPayCosts().getCostMana();
            ManaCostBeingPaid toPay = new ManaCostBeingPaid(manaCost.getMana());
            toPay.setXManaCostPaid(this.value != null ? this.value : 0, focusSpellAbility.getXColor());
            return currentEstimate.copyWithDefinedMana(toPay.toManaCost());
        }
    }

    abstract static class Order<T extends IIdentifiable> extends ActionItemChoice<ChoiceConstraints.Ordering<T>, List<T>> {
        protected final List<? extends ITestReference<T>> order;
        protected final boolean reversed;

        Order(GameLogicTestActionQueue queue, List<? extends ITestReference<T>> order, boolean reversed) {
            super(queue);
            this.order = order;
            this.reversed = reversed;
        }

        @Override
        Set<ICardReference> getCardRefs() {
            return order.stream().filter(ICardReference.class::isInstance).map(ICardReference.class::cast).collect(Collectors.toSet());
        }

        @Override
        List<T> evaluate(Game game, ChoiceConstraints.Ordering<T> constraints) {
            Collection<T> options = constraints.pool();
            Set<T> unused = new HashSet<>(options);
            List<T> out = new ArrayList<>(order.stream().mapToInt(ITestReference::getQuantity).sum());
            for(ITestReference<T> ref : this.order) {
                Set<T> matchingOptions = unused.stream().filter(ref::refersTo).limit(ref.getQuantity()).collect(Collectors.toSet());
                unused.removeAll(matchingOptions);
                if(matchingOptions.isEmpty())
                    throw new ChoiceConstraints.ChoiceConstraintException(this, "Choosing Order - Missing item %s; Options: %s", ref, options);
                if(matchingOptions.size() < ref.getQuantity())
                    throw new ChoiceConstraints.ChoiceConstraintException(this, "Choosing Order - Missing copies of %s; Expected %d, found %s; Options: %s", ref, ref.getQuantity(), matchingOptions.size(), options);
                out.addAll(matchingOptions);
            }
            if(!unused.isEmpty()) {
                throw new ChoiceConstraints.ChoiceConstraintException(this, "Choosing Order - Unused options found: %s", unused);
//                this.queue.log("Choosing Order - Unused options found: %s", unused);
//                out.addAll(unused);
            }
            if(reversed)
                Collections.reverse(out);
            return out;
        }

    }

    static class OrderStack extends Order<SpellAbility> {
        OrderStack(GameLogicTestActionQueue queue, List<? extends ITestReference<SpellAbility>> order, boolean reversed) {
            super(queue, order, reversed);
        }

        @Override
        public String toString() {
            return "OrderStack[" + order.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
        }
    }




    //    static class ActionItemChoice_Payment extends ActionItemChoice {
//
//    }
}
