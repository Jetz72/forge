package forge.game.logic;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.spellability.SpellAbility;

import java.util.Set;

/**
 * Represents an action to attempt next time this player has priority.
 */
public class ActionItemPriority extends GameLogicTestActionQueue.ActionItem implements GameLogicTestActionQueue.HasFocusAdjustment {
    final ActionType type;
    final CardReference subject;
    final int saIndex;
    final String saKeyword;

    protected enum ActionType {
        PLAY,
        ACTIVATE,
        RESPOND,
        RESPOND_ACTIVATE,
        LAND_DROP,
        SPECIAL_ACTION;
        //Concede?

        boolean needEmptyStack() {
            return this == PLAY || this == ACTIVATE || this == LAND_DROP;
        }

        boolean isSpell() {
            return this == PLAY || this == RESPOND;
        }

        boolean isAbility() {
            return this == ACTIVATE || this == RESPOND_ACTIVATE;
        }

        boolean isSpecialAction() {
            return this == SPECIAL_ACTION || this == LAND_DROP;
        }
    }

    private ActionItemPriority(GameLogicTestActionQueue queue, ActionType type, CardReference subject, int saIndex, String saKeyword) {
        super(queue);
        this.type = type;
        this.subject = subject;
        this.saIndex = saIndex;
        this.saKeyword = saKeyword;
        subject.assertSingular();
    }

    protected ActionItemPriority(GameLogicTestActionQueue queue, ActionType type, CardReference subject, int saIndex) {
        this(queue, type, subject, saIndex, null);
    }

    protected ActionItemPriority(GameLogicTestActionQueue queue, ActionType type, CardReference subject, String saKeyword) {
        this(queue, type, subject, -1, saKeyword);
    }

    @Override
    public String toString() {
        return String.format("[%s] - %s(%s)", this.type.name(), this.subject.toString(), this.saKeyword == null ? String.valueOf(this.saIndex) : this.saKeyword);
    }

    @Override
    Set<CardReference> getCardRefs() {
        if (subject == null)
            return Set.of();
        return Set.of(subject);
    }

    //TODO: Handle alt face casts.
    public SpellAbility getSpellAbility() {
        //TODO: Return a list that includes all sub-abilities?
        if (this.saKeyword != null) //TODO: Find by keyword.
            throw new UnsupportedOperationException("TODO: keyword-based ability lookup not yet implemented for " + saKeyword);
        if (saIndex < 0)
            return null;
        if (this.type.isSpell()) {
            /* CardType type = paperCard.getRules().getType();
            if(!type.isInstant() && !type.isSorcery())
                return null; //Just play the card.*/
            return GameLogicTestUtils.getSpell(this.subject.getCard(), saIndex);
        } else if (this.type.isAbility()) {
            return GameLogicTestUtils.getActivatedAbility(this.subject.getCard(), saIndex);
        } else if (this.type.isSpecialAction()) {
                return GameLogicTestUtils.getSpecialAction(this.subject.getCard(), saIndex);
        } else {
            //Shouldn't even be able to call this with no associated card.
            assert (false);
            return null;
        }
    }

    SpellAbility getSpellAbility(Game game) {
        Card card = this.subject.findCards(game).get(0);
        if (this.type.isSpell())
            return GameLogicTestUtils.getSpell(card, this.saIndex);
        else if (this.type.isAbility())
            return GameLogicTestUtils.getActivatedAbility(card, this.saIndex);
        else if (this.type.isSpecialAction())
            return GameLogicTestUtils.getSpecialAction(card, saIndex);
        else {
            assert (false);
            return null;
        }
    }

    Cost getEstimatedCost() { //TODO: Inline?
        SpellAbility sa = getSpellAbility();
        if (sa == null)
            return Cost.Zero;
        return sa.getPayCosts();
    }

    boolean isReady(GameLogicTestPlayerController controller) {
        if (!matchesPlayer(controller))
            return false;
        Game game = controller.getGame();
        if (this.type.needEmptyStack()) {
            //Not playing in response; stack must be empty.
            if (!game.getStack().isEmpty())
                return false;
        }

        SpellAbility sa = getSpellAbility(game);

        sa.setActivatingPlayer(controller.getPlayer());
        if (!sa.canPlay(true))
            return false;

        return true;
    }
}
