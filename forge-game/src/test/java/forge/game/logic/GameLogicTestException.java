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
            out[i] = getLogString(formatParams[i]);
        }
        return out;
    }

    protected static Object getLogString(Object o) {
        Object replacement;
        if (o instanceof Player p)
            replacement = p.getName();
        else if (o instanceof Card c)
            replacement = String.format("[ID#%d %s]", c.getId(), c.getName()); //Would like to get the original reference, but we'd need to get the reference pool here.
        else if (o instanceof SpellAbility sa) {
            Card c = sa.getHostCard();
            replacement = String.format("[ID#%d %s: %s]", c.getId(), c.getName(), StringUtils.abbreviate(sa.toString(), 32));
        } //Collections of game objects can be stringified normally.
        else if (o instanceof GameLogicTestActionQueue.ActionItem actionItem)
            replacement = String.format("(%s%s)", actionItem.isInverted() ? "[N!] " : "", actionItem);
        else if (o instanceof TestReference)
            replacement = String.format("<%s>", o);
        else if (o instanceof List<?> list)
            replacement = "[" + Arrays.stream(processFormatParams(list.toArray())).map(Object::toString).collect(Collectors.joining(", ")) + "]";
        else if (o instanceof Set<?> set)
            replacement = "[" + Arrays.stream(processFormatParams(set.toArray())).map(Object::toString).collect(Collectors.joining(", ")) + "]";
        else if (o instanceof Integer)
            replacement = o;
        else
            replacement = String.valueOf(o);
        return replacement;
    }
}
