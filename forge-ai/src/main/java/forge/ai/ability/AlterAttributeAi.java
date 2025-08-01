package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.SpellAbilityAi;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;

import java.util.List;
import java.util.Map;

public class AlterAttributeAi extends SpellAbilityAi {

    @Override
    protected AiAbilityDecision checkApiLogic(Player aiPlayer, SpellAbility sa) {
        final Card source = sa.getHostCard();
        boolean activate = Boolean.parseBoolean(sa.getParamOrDefault("Activate", "true"));
        String[] attributes = sa.getParam("Attributes").split(",");

        if (sa.usesTargeting()) {
            // TODO add targeting logic
            // needed for Suspected
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }

        final List<Card> defined = AbilityUtils.getDefinedCards(source, sa.getParam("Defined"), sa);

        for (Card c : defined) {
            for (String attr : attributes) {
                switch (attr.trim()) {
                    case "Solve":
                    case "Solved":
                        // there is currently no effect that would un-solve something
                        if (!c.isSolved() && activate) {
                            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                        }
                        break;
                    case "Suspect":
                    case "Suspected":
                        // is Suspected good or bad?
                        // currently Suspected is better
                        if (!activate) {
                            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
                        }
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);

                    case "Saddle":
                    case "Saddled":
                        // AI should not try to Saddle again?
                        if (c.isSaddled()) {
                            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
                        }
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
            }
        }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    protected boolean checkPhaseRestrictions(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        final Card source = sa.getHostCard();
        String[] attributes = sa.getParam("Attributes").split(",");

        // currently Phase is only checked for Saddled

        for (String attr : attributes) {
            switch (attr.trim()) {
                case "Saddle":
                case "Saddled":
                    if (!ph.isPlayerTurn(ai)) {
                        return false;
                    }
                    // it is too late for combat, Saddle is Sorcery Speed
                    if (!ph.getPhase().isBefore(PhaseType.COMBAT_BEGIN)) {
                        return false;
                    }
                    // would card attack?
                    if (!CombatUtil.canAttack(source)) {
                        return false;
                    }
            }
        }

        return true;
    }

    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message, Map<String, Object> params) {
        boolean activate = Boolean.parseBoolean(sa.getParamOrDefault("Activate", "true"));
        String[] attributes = sa.getParam("Attributes").split(",");

        for (String attr : attributes) {
            switch (attr.trim()) {
                case "Suspect":
                case "Suspected":
                    return activate;
            }
        }

        return true;
    }
}
