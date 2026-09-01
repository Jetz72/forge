package forge.game.logic;

import forge.game.Game;
import forge.game.card.Card;

import java.util.List;

/* package */ interface ICardReference extends ITestReference<Card> {
    @Override
    default CardReference ensureConcrete() {
        if (this instanceof CardReference c)
            return c;
        throw new IllegalArgumentException("Expected a concrete reference here.");
    }

    List<Card> findCards(Game game); //TODO: Probably not needed; can likely remove.
    boolean needsPlacementDuringSetup();
}
