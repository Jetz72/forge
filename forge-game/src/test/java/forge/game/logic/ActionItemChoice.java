package forge.game.logic;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.cost.Cost;
import forge.game.cost.CostPartMana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a decision to make while resolving a spell.
 */
abstract class ActionItemChoice extends GameLogicTestActionQueue.ActionItem
        implements GameLogicTestActionQueue.HasImplicitSetup, GameLogicTestActionQueue.HasCostAdjustment {
    protected ActionItemChoice(GameLogicTestActionQueue queue) {
        super(queue);
    }

    @Override
    public void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility) {}

    @Override
    public Cost adjustEstimatedCost(ActionItemPriority lastPriority, Cost currentEstimate) {
        return currentEstimate;
    }

    static class Target extends ActionItemChoice {
        final List<GameLogicTestReference> targets;
        private final List<CardReference> cardTargets;
        private final List<PlayerReference> playerTargets;
        private final CardReference sourceCard;

        Target(GameLogicTestActionQueue queue, GameLogicTestReference... targets) {
            super(queue);
            this.targets = List.of(targets);
            this.cardTargets = Arrays.stream(targets)
                    .filter(CardReference.class::isInstance)
                    .map(CardReference.class::cast)
                    .collect(Collectors.toList());
            this.playerTargets = Arrays.stream(targets)
                    .filter(PlayerReference.class::isInstance)
                    .map(PlayerReference.class::cast)
                    .collect(Collectors.toList());
            this.sourceCard = queue.getTargetingFor();
            if(sourceCard == null)
                throw new IllegalStateException("Can't assign a target in this context. Need a preceding priority action or expectTrigger.");
            else if(sourceCard.getQuantity() > 1)
                throw new UnsupportedOperationException("Can't assign a target for a multi-card reference (" + sourceCard + ")");
        }

        @Override
        Set<CardReference> getCardRefs() {
            return Set.copyOf(this.cardTargets);
        }

        List<GameEntity> getResolvedTargets(Game game) {
            ArrayList<GameEntity> out = new ArrayList<>(cardTargets.size() + playerTargets.size());
            for(CardReference r : cardTargets)
                out.addAll(r.findCards(game));
            for(PlayerReference r : playerTargets)
                out.add(r.findPlayer(game));
            return out;
        }

        /* package */ boolean matchesSpellAbility(SpellAbility ability) {
            if(sourceCard == null)
                return true;
            return sourceCard.refersTo(ability.getHostCard());
        }

        @Override
        public void doImplicitSetup(ActionItemPriority lastPriority, SpellAbility focusSpellAbility) {
            super.doImplicitSetup(lastPriority, focusSpellAbility);
            if (focusSpellAbility == null && lastPriority.getSpellAbility() != null) {
                if(this.cardTargets.stream().anyMatch(c -> c.zone == null || c.ownerIndex == -1))
                    queue.log("Unable to infer owner or zone for targets of %s. Trying to target non-intrinsic ability?", lastPriority.subject);
                return;
            }
            TargetRestrictions tr = GameLogicTestUtils.findTargetRestrictions(focusSpellAbility);
            if (tr == null)
                throw new UnsupportedOperationException("Tried to assign target for " + focusSpellAbility + ", but the ability does not target.");

            List<ZoneType> zones = tr.getZone();
            ZoneType zone = zones.contains(ZoneType.Battlefield) ? ZoneType.Battlefield : zones.get(0);
            for(CardReference target : this.cardTargets)
                target.setInferredZone(zone);

            int targetOwnerIndex = GameLogicTestUtils.targetsEnemyCard(tr) ? getOpponentIndex() : getPlayerIndex();
            for(CardReference target : this.cardTargets)
                target.setInferredOwner(targetOwnerIndex);
        }
    }

    static abstract class Value<T> extends ActionItemChoice {
        final T value;

        Value(GameLogicTestActionQueue queue, T value) {
            super(queue);
            this.value = value;
        }

        @Override Set<CardReference> getCardRefs() { return Set.of(); }

        public T getValue() { return this.value; }
    }

    static class Number extends Value<Integer> {
        Number(GameLogicTestActionQueue queue, Integer value) { super(queue, value); }
    }

    static class XValue extends Number {
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




    //    static class ActionItemChoice_Payment extends ActionItemChoice {
//
//    }
}
