package forge.game.logic;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.spellability.AbilityStatic;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.trigger.Trigger;
import forge.item.PaperCard;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class GameLogicTestUtils {
    private GameLogicTestUtils() { }

    public static Card buildCard(PaperCard paperCard) {
        return CardFactory.getCard(paperCard, null, 0, null);
    }

    public static SpellAbility getSpell(PaperCard paperCard, int saIndex) {
        return nthIntrinsic(buildCard(paperCard), SpellAbility::isSpell, saIndex, paperCard.getName());
    }

    public static SpellAbility getSpell(Card card, int saIndex) {
        return nthIntrinsic(card, SpellAbility::isSpell, saIndex, card.getPaperCard().getName());
    }

    public static SpellAbility getActivatedAbility(PaperCard paperCard, int saIndex) {
        return nthIntrinsic(buildCard(paperCard), SpellAbility::isActivatedAbility, saIndex, paperCard.getName());
    }

    public static SpellAbility getActivatedAbility(Card card, int saIndex) {
        return nthIntrinsic(card, SpellAbility::isActivatedAbility, saIndex, card.getPaperCard().getName());
    }

    public static SpellAbility getSpecialAction(PaperCard paperCard, int saIndex) {
        return nthAbilityStatic(buildCard(paperCard), saIndex, paperCard.getName());
    }

    public static SpellAbility getSpecialAction(Card card, int saIndex) {
        return nthAbilityStatic(card, saIndex, card.getPaperCard().getName());
    }

    public static Trigger getTrigger(PaperCard paperCard, int triggerIndex) {
        return nthTrigger(buildCard(paperCard), triggerIndex, paperCard.getName());
    }

    public static Trigger getTrigger(PaperCard paperCard, Class<? extends Trigger> apiType) {
        return buildCard(paperCard).getTriggers().stream().filter(apiType::isInstance).findFirst().orElse(null);
    }

    public static Trigger getTrigger(Card card, int triggerIndex) {
        return nthTrigger(card, triggerIndex, card.getPaperCard().getName());
    }

    private static SpellAbility nthIntrinsic(Card card, Predicate<SpellAbility> filter, int saIndex, String cardName) {
        int i = 0;
        for (SpellAbility sa : card.getSpellAbilities()) {
            if (!sa.isIntrinsic() || !filter.test(sa))
                continue;
            if (i == saIndex)
                return sa;
            i++;
        }
        throw new RuntimeException("Can't find spell ability " + saIndex + " in card script " + cardName);
    }

    private static SpellAbility nthAbilityStatic(Card card, int saIndex, String cardName) {
        int i = 0;
        for (SpellAbility sa : card.getSpellAbilities()) {
            if (!(sa instanceof AbilityStatic))
                continue;
            if (i == saIndex)
                return sa;
            i++;
        }
        throw new RuntimeException("Can't find special action ability " + saIndex + " in card " + cardName);
    }

    private static Trigger nthTrigger(Card card, int saIndex, String cardName) {
        if(card.getTriggers().size() <= saIndex)
            throw new RuntimeException("Can't find trigger " + saIndex + " in card script " + cardName);
        return card.getTriggers().get(saIndex);
    }

    /** Walks a SpellAbility's own targeting, then its SubAbility chain, for the first TargetRestrictions found. */
    public static TargetRestrictions findTargetRestrictions(SpellAbility sa) {
        //TODO: Nth instance of target as a parameter.
        for (SpellAbility node = sa; node != null; node = node.getSubAbility()) {
            if (node.usesTargeting())
                return node.getTargetRestrictions();
        }
        return null;
    }

    private static final Set<String> ENEMY_TARGET_HINTS = Set.of("OppCtrl", "YouDontCtrl", "OppOwn", "YouDontOwn");
    public static boolean targetsEnemyCard(TargetRestrictions restrictions) {
        if(restrictions == null)
            return false;
        String[] validTgts = restrictions.getValidTgts();
        for (String validTgt : validTgts) {
            if(ENEMY_TARGET_HINTS.stream().noneMatch(validTgt::contains))
                return false;
        }
        return true;
    }

    private static final Map<Byte, String> colorToLand = Map.of(
            MagicColor.WHITE, "Plains",
            MagicColor.BLUE, "Island",
            MagicColor.BLACK, "Swamp",
            MagicColor.RED, "Mountain",
            MagicColor.GREEN, "Forest",
            MagicColor.COLORLESS, "Wastes"
    );

    public static Map<String, Integer> getLandsForCost(ManaCost cost) {
        Map<String, Integer> out = new HashMap<>(6);
        for (ManaCostShard shard : cost) {
            if(shard.isSnow()) {
                out.merge("Snow-Covered Wastes", 1, Integer::sum);
                continue;
            }
            if(shard.isGeneric()) {
                //Probably won't happen?
                out.merge("Wastes", shard.getCmc(), Integer::sum);
                continue;
            }
            for(byte color : MagicColor.WUBRGC) {
                if (shard.isColor(color)) {
                    out.merge(colorToLand.get(color), 1, Integer::sum);
                    break;
                }
            }
        }
        if (cost.getGenericCost() > 0)
            out.merge("Wastes", cost.getGenericCost(), Integer::sum);
        return out;
    }
}
