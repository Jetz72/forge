package forge.game.logic;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GameLogicTestException extends RuntimeException {
    public GameLogicTestException(String message) {
        super(message);
    }

    public GameLogicTestException(String message, Object... formatParams) {
        super(String.format(message, processFormatParams(formatParams)));
    }

    protected static Object[] processFormatParams(Object... formatParams) {
        Object[] out = new Object[formatParams.length];
        for (int i = 0; i < formatParams.length; i++) {
            Object o = formatParams[i];
            if (o instanceof Player p)
                out[i] = p.getName();
            else if (o instanceof Card c)
                out[i] = String.format("[ID#%d %s]", c.getId(), c.getName()); //Would like to get the original reference, but we'd need to get the reference pool here.
            else if (o instanceof SpellAbility sa) {
                Card c = sa.getHostCard();
                out[i] = String.format("[ID#%d %s: %s]", c.getId(), c.getName(), StringUtils.abbreviate(sa.toString(), 32));
            } //Collections of game objects can be stringified normally.
            else if (o instanceof GameLogicTestActionQueue.ActionItem)
                out[i] = String.format("(%s)", o);
            else if (o instanceof TestReference)
                out[i] = String.format("<%s>", o);
            else if (o instanceof List<?> list)
                out[i] = "[" + Arrays.stream(processFormatParams(list.toArray())).map(Object::toString).collect(Collectors.joining(", ")) + "]";
            else if (o instanceof Set<?> set)
                out[i] = "[" + Arrays.stream(processFormatParams(set.toArray())).map(Object::toString).collect(Collectors.joining(", ")) + "]";
            else
                out[i] = o;
        }
        return out;
    }
}
