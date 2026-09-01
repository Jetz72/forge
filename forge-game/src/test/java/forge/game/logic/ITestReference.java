package forge.game.logic;

import forge.game.Game;
import forge.game.IIdentifiable;

import java.util.Set;

/* package */ interface ITestReference <T extends IIdentifiable> {
    default ICardReference ensureCard() {
        if (this instanceof ICardReference c)
            return c;
        throw new IllegalArgumentException("Expected a card reference here.");
    }

    default PlayerReference ensurePlayer() {
        if (this instanceof PlayerReference p)
            return p;
        throw new IllegalArgumentException("Expected a player reference here.");
    }

    default TestReference<T> ensureConcrete() {
        if (this instanceof TestReference<T> t)
            return t;
        throw new IllegalArgumentException("Expected a concrete reference here.");
    }

    boolean refersTo(IIdentifiable o);

    /* package */  Set<T> getResolved(Game game);

    int getQuantity();

    default void assertSingular() {
        this.assertSingular("Quantity > 1 is not allowed here.");
    }

    default void assertSingular(String error) {
        if (this.getQuantity() > 1)
            throw new UnsupportedOperationException(error + String.format(" (%s)", this));
    }
}
