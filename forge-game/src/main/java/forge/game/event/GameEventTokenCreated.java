package forge.game.event;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.util.collect.FCollection;

import java.util.Collection;
import java.util.stream.Collectors;

public record GameEventTokenCreated(FCollection<CardView> tokens) implements GameEvent {

    public GameEventTokenCreated(Collection<Card> tokens) {
        this(CardView.getCollection(tokens));
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Token created - " + tokens.stream().map(CardView::getOracleName).collect(Collectors.toSet());
    }
}
