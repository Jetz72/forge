package forge.game.logic;

import forge.game.GameObject;
import forge.game.spellability.SpellAbility;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/*package*/ interface ChoiceConstraints <R> {
    record Selection<T>(Collection<T> pool, int minSelections, int maxSelections, List<SelectionRule<T>> rules) implements ChoiceConstraints<Set<T>> {}
    record SelectionRule<T>(String description, Predicate<Collection<T>> test) {}
    record Target(Collection<? extends GameObject> pool, SpellAbility spellAbility) implements ChoiceConstraints<Set<GameObject>> {}
    record Range(Integer min, Integer max) implements ChoiceConstraints<Integer> {}
    record Ordering<T>(Collection<T> pool) implements ChoiceConstraints<List<T>> {}

    class ChoiceConstraintException extends GameLogicTestException {
        private final ActionItemChoice<?, ?> choice;
        /* package */ ChoiceConstraintException(ActionItemChoice<?, ?> choice, String message, Object... formatParams) {
            super(message, formatParams);
            this.choice = choice;
        }

        @Override
        public String getMessage() {
            return String.format("Constraint not met for choice [%s]: %s", this.choice, super.getMessage());
        }
    }
}
