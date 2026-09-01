package forge.game.logic;

import forge.game.Game;
import forge.game.player.Player;

import java.util.Set;
import java.util.regex.Pattern;

public class PlayerReference extends TestReference<Player> {
    static final Pattern PLAYER_REFERENCE_PATTERN = Pattern.compile("^\\s*Player (?<index>\\d+)\\s*$");
    static final Set<String> RELATIVE_REF_NAMES = Set.of("Self", "Opponent"); //Teammate?
    final int playerIndex;

    /* package */ PlayerReference(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    public Player findPlayer(Game game) {
        Player player = game.getPlayer(this.id);
        if(player == null)
            throw new NullPointerException("Cannot find player id " + id + " in given game!");
        return player;
    }

    @Override
    public Set<Player> getResolved(Game game) {
        return Set.of(findPlayer(game));
    }

    @Override
    public String toString() {
        return String.format("Player %d", playerIndex + 1);
    }
}
