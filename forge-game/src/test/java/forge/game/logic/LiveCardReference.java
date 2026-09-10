package forge.game.logic;

import forge.game.Game;
import forge.game.IIdentifiable;
import forge.game.card.Card;
import forge.game.card.CardView;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LiveCardReference extends LiveReference<Card> implements ICardReference {

    LiveCardReference(String label) {
        super(label);
    }

    void setResolved(Collection<? extends IIdentifiable> resolved) {
        for (IIdentifiable i : resolved) {
            if (!(i instanceof Card || i instanceof CardView))
                throw new GameLogicTestException("%s - expected to resolve to type 'Card' or 'CardView'; got '%s'", this, i.getClass());
        }
        super.setResolved(resolved);
    }

    @Override
    public Set<Card> getResolved(Game game) {
        return this.resolved.stream().map(game::findById).collect(Collectors.toSet());
    }

    @Override
    public List<Card> findCards(Game game) {
        return List.copyOf(getResolved(game));
    }

    @Override
    public boolean refersTo(IIdentifiable o) {
        return (o instanceof Card || o instanceof CardView) && this.resolved.contains(o.getId());
    }

    @Override
    public boolean needsPlacementDuringSetup() {
        return false;
    }
}
