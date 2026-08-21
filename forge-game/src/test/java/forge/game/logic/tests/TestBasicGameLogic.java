package forge.game.logic.tests;

import forge.game.logic.GameLogicTest;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

public class TestBasicGameLogic extends GameLogicTest {
    @Test
    void testBoltBird() {
        cast("Lightning Bolt").target("Birds of Paradise");
        expectDamage(3, "Birds of Paradise");
        assertZone(ZoneType.Graveyard, "Birds of Paradise");
    }

    @Test
    void testSpellBattle() {
        opponent.setup.battlefield("Birds of Paradise");
        user.cast("Lightning Bolt").target("Birds of Paradise");
        opponent.respond("Giant Growth").target("Birds of Paradise");
        user.respond("Counterspell").target("Giant Growth");
        assertZone(ZoneType.Graveyard, "Birds of Paradise", "Lightning Bolt", "Counterspell");
    }

    @Test
    void testAOEDamage() {
        opponent.setup.battlefield("[3x] Grizzly Bears");
        user.activate("Bloodfire Colossus");
        expectDamage(6, "[3x] Grizzly Bears", "Player 1", "Player 2");
        assertZone(ZoneType.Graveyard, "Bloodfire Colossus", "[3x] Grizzly Bears");
    }

    @Test
    void testCardDraw() {
        cast("Prosperity").withXValue(3).drawing("Grizzly Bears", "Plains", "Mikokoro, Center of the Sea");
        opponent.drawing("[3x] Mountain");
        user.playLand("Mikokoro, Center of the Sea");
        assertZone(ZoneType.Hand, "Grizzly Bears", "Plains", "[3x] Mountain");
        assertZone(ZoneType.Battlefield, "Mikokoro, Center of the Sea");
    }

    @Test
    void testTriggers() {
        user.setup.battlefield("Gristle Grinner", "Omnath, Locus of Rage", "Bogardan Firefiend");
        opponent.setup.battlefield("[#1] Plague Spitter", "[#2] Plague Spitter");
        user.playLand("Mountain").expectTrigger("Omnath, Locus of Rage", 0);
        user.cast("Lightning Bolt").target("[#1] Plague Spitter");
        user.respond("Lava Dart").target("[#2] Plague Spitter").expectDamage(1, "[#2] Plague Spitter");
        expectDeath("[#1] Plague Spitter");
        then(); //Triggers from 1st Plague Spitter's death go on stack.
        expectTrigger("Gristle Grinner").expectTrigger("[#1] Plague Spitter");
        then(); //Resolve stack.
        expectDamage(1, "Player 1", "Player 2", "Bogardan Firefiend", "Gristle Grinner", "Omnath, Locus of Rage", "[#2] Plague Spitter"); //TODO: More live-reference stuff. Support tokens, stack instances, and extrinsic abilities.
        expectDeath("[#2] Plague Spitter", "Bogardan Firefiend");
        then(); //Death triggers from those two go on the stack. TODO: Ordering.
        expectTrigger("[#2] Plague Spitter");
        user.expectTrigger("Bogardan Firefiend").target("Omnath, Locus of Rage");
        user.expectTrigger("Omnath, Locus of Rage", 1).target("Player 2");
        expectTriggers("Gristle Grinner", 2);
        then(); //Resolve all those.
        expectDamage(1, "Player 1", "Player 2", "Omnath, Locus of Rage", "Gristle Grinner"); //Plague Spitter
        expectDamage(2, "Omnath, Locus of Rage"); //Firefiend
        expectDamage(3, "Player 2"); //Omnath
        assertPT(9, 9, "Gristle Grinner");
    }
}
