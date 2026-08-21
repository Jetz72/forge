package forge.game.logic;

import forge.game.cost.*;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.spellability.SpellAbility;

public class GameLogicTestCostDecisionMaker extends CostDecisionMakerBase {

    final PlayerController controller;

    public GameLogicTestCostDecisionMaker(final PlayerController controller, final Player p, final SpellAbility sa, final boolean effect) {
        super(p, effect, sa, sa.getHostCard());
        this.controller = controller;
    }

    @Override
    public boolean paysRightAfterDecision() {
        return true;
    }

    @Override
    public PaymentDecision visit(CostBehold cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostBeholdExile cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostGainControl cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostChooseColor cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostChooseCreatureType cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostCollectEvidence cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostDiscard cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostDamage cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostDraw cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostExile cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostExileFromStack cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostExiledMoveToGrave cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostExert cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostEnlist cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostFlipCoin cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostForage cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostRollDice cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostMill cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostAddMana cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPayLife cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPayEnergy cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostGainLife cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPartMana cost) {
        //Mana costs are handled in controller.payManaCost. Return value is ignored aside from it being null or not.
        return new PaymentDecision(0);
    }

    @Override
    public PaymentDecision visit(CostPromiseGift cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPutCardToLib cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostTap cost) {
        //This is a self-tap cost; no decision to be made.
        //TODO: Consider moving this one up to the base class.
        return new PaymentDecision(0);
    }

    @Override
    public PaymentDecision visit(CostSacrifice cost) {
        if (cost.payCostFromSource()) {
            return PaymentDecision.card(source);
        }
        if (cost.getType().equals("OriginalHost")) {
            return PaymentDecision.card(ability.getOriginalHost());
        }
        //TODO: Check action queue.
        return null;
    }

    @Override
    public PaymentDecision visit(CostReturn cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostReveal cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostRevealChosen cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostRemoveAnyCounter cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostRemoveCounter cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPutCounter cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPutCounterYou cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostUntapType cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostUntap cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostUnattach cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostTapType cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostPayShards cost) {
        return null;
    }

    @Override
    public PaymentDecision visit(CostBlight cost) {
        return null;
    }
}
