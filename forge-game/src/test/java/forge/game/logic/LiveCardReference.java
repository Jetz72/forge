package forge.game.logic;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.IIdentifiable;
import forge.game.card.Card;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LiveCardReference extends LiveReference<Card> implements ICardReference {

    protected Set<Integer> ids;

    LiveCardReference(String label) {
        super(label);
    }

    @Override
    void setResolved(Collection<Card> resolved) {
        super.setResolved(resolved);
        this.ids = resolved.stream().map(GameEntity::getId).collect(Collectors.toSet());
    }

    @Override
    public Set<Card> getResolved(Game game) {
        return ids.stream().map(game::findById).collect(Collectors.toSet());
    }

    @Override
    public List<Card> findCards(Game game) {
        return List.copyOf(getResolved(game));
    }

    @Override
    public boolean refersTo(IIdentifiable o) {
        return o instanceof Card && ids.contains(o.getId());
    }

    @Override
    public boolean needsPlacementDuringSetup() {
        return false;
    }
}
