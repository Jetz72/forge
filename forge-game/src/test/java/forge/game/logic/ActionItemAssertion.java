package forge.game.logic;

import com.google.common.collect.Maps;
import forge.game.Game;
import forge.game.card.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class ActionItemAssertion extends GameLogicTestActionQueue.ActionItem {
    List<CardReference> cards;

    ActionItemAssertion(GameLogicTestActionQueue queue, CardReference... cards) {
        super(queue);
        this.cards = List.of(cards);
    }

    @Override
    Set<CardReference> getCardRefs() {
        return Set.copyOf(this.cards);
    }

    protected Stream<Card> streamResolvedCards(Game game) {
        return this.getCardRefs().stream()
                .flatMap(ref -> ref.findCards(game).stream());
    }

    protected Map<CardReference, List<Card>> getResolvedCardMap(Game game) {
        return Maps.asMap(getCardRefs(), ref -> ref.findCards(game));
    }

    protected <T> void assertForAll(Game game, Function<Card, T> function, T expected, String traitName) {
        List<CardReference> failures = null;
        Map<CardReference, List<Card>> cardMap = getResolvedCardMap(game);
        for (CardReference ref : this.getCardRefs()) {
            if (cardMap.get(ref).stream().map(function).allMatch(expected::equals))
                continue;
            if (failures == null) failures = new ArrayList<>();
            failures.add(ref);
        }
        if (failures == null) {
            queue.log("Assertion Passed: %s.%s = %s", this.cards, traitName, expected);
            return;
        }
        String error;
        if (failures.size() == 1) {
            CardReference ref = failures.get(0);
            Set<T> values = cardMap.get(ref).stream().map(function).collect(Collectors.toSet());
            String valueText = values.size() == 1 ? values.iterator().next().toString() : values.toString();
            error = String.format("Unexpected [%s] of card %s - Expected: %s, Actual: %s", traitName, ref.toString(), expected, valueText);
        } else {
            StringBuilder sb = new StringBuilder(String.format("Unexpected [%s] for %d card references. Expected: %s", traitName, failures.size(), expected));
            for (CardReference ref : failures) {
                Set<T> values = cardMap.get(ref).stream().map(function).collect(Collectors.toSet());
                String valueText = values.size() == 1 ? values.iterator().next().toString() : values.toString();
                sb.append(String.format("\n\t%s - Actual: %s", ref.toString(), valueText));
            }
            error = sb.toString();
        }
        throw new GameLogicTestException(error); //TODO: Inline this; can format it inside.
    }

    public abstract void doAssert(Game game);
}
