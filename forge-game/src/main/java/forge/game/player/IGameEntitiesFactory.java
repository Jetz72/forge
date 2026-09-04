package forge.game.player;

import forge.game.Game;

public interface IGameEntitiesFactory { //TODO: Maybe shove LobbyPlayer down to the game module and just merge this in.
	PlayerController createMindSlaveController(Player master, Player slave);
	Player createIngamePlayer(Game game, int id);
}
