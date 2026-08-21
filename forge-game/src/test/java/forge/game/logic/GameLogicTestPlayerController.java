package forge.game.logic;

import com.google.common.collect.*;
import forge.LobbyPlayer;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.*;
import forge.game.event.GameEvent;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.mana.ManaPool;
import forge.game.player.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMustAttack;
import forge.game.staticability.StaticAbilityMustTarget;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Aggregates;
import forge.util.ITriggerEvent;
import forge.util.IterableUtil;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GameLogicTestPlayerController extends PlayerController {

    private final GameLogicTestActionQueue queue;
    private final int playerIndex;

    public GameLogicTestPlayerController(Game game, Player p, LobbyPlayer lp, int playerIndex, GameLogicTestActionQueue queue) {
        super(game, p, lp);
        this.queue = queue;
        this.playerIndex = playerIndex;
    }

    protected void log(String message, Object... formatParams) {
        Object[] newParams = new Object[formatParams.length + 1];
        newParams[0] = this.player;
        System.arraycopy(formatParams, 0, newParams, 1, formatParams.length);
        this.queue.log("%s: " + message, newParams);
    }


    @Override
    public SpellAbility getAbilityToPlay(Card hostCard, List<SpellAbility> abilities, ITriggerEvent triggerEvent) {
        int desiredIndex = queue.currentPriority().saIndex; //TODO: This is kinda naive, and doesn't account for changed abilities and other filters. Need a better lookup process.
        if(desiredIndex >= abilities.size())
            throw new GameLogicTestException("Unable to use ability #%d of card %s. \nAvailable: %s", desiredIndex, hostCard, abilities.toString());
        return abilities.get(desiredIndex);
    }

    @Override
    public void playSpellAbilityNoStack(SpellAbility effectSA, boolean mayChoseNewTargets) {
        // Very different logic paths for human and AI. Using the human path here.
        effectSA.setActivatingPlayer(player);
        final PlaySpellAbility req = new PlaySpellAbility(this, effectSA);
        req.playAbility(mayChoseNewTargets, false, true);
    }

    @Override
    public List<SpellAbility> orderSimultaneousSa(List<SpellAbility> activePlayerSAs) {
        //TODO: Check action queue.
        return activePlayerSAs;
    }

    @Override
    public void orderAndPlaySimultaneousSa(List<SpellAbility> activePlayerSAs) {
        for(SpellAbility sa : activePlayerSAs) {
            if (sa.isTrigger()) {
                GameEvent event = new TestGameEvent.Trigger(sa);
                getGame().fireEvent(event);
            }
        }
        //TODO: Taken verbatim from PlayerControllerHuman. Maybe fold that else branch into playSpellAbility?
        List<SpellAbility> orderedSAs = orderSimultaneousSa(activePlayerSAs);
        for (int i = orderedSAs.size() - 1; i >= 0; i--) {
            final SpellAbility next = orderedSAs.get(i);
            if (next.isTrigger() && !next.isCopied()) {
                PlaySpellAbility.playSpellAbility(this, player, next);
            } else {
                if (next.isCopied()) {
                    if (next.isSpell()) {
                        // copied spell always add to stack
                        if (!next.getHostCard().isInZone(ZoneType.Stack)) {
                            next.setHostCard(player.getGame().getAction().moveToStack(next.getHostCard(), next));
                        } else {
                            player.getGame().getStackZone().add(next.getHostCard());
                        }
                    }
                    if (next.isMayChooseNewTargets()) {
                        next.setupNewTargets(player);
                    }
                }
                player.getGame().getStack().add(next);
            }
        }
    }

    @Override
    public boolean playTrigger(Card host, WrappedAbility wrappedAbility, boolean isMandatory) {
        //For humans, functionally identical to playSpellAbilityNoStack.
        wrappedAbility.setActivatingPlayer(player);
        final PlaySpellAbility req = new PlaySpellAbility(this, wrappedAbility);
        return req.playAbility(true, false, true);
    }

    @Override
    public boolean playSaFromPlayEffect(SpellAbility tgtSA) {
        return PlaySpellAbility.playSpellAbility(this, player, tgtSA);
    }

    @Override
    public List<PaperCard> sideboard(Deck deck, GameType gameType, String message) {
        //Out of scope for GameLogicTests.
        return deck.get(DeckSection.Main).toFlatList();
    }
    @Override
    public List<PaperCard> chooseCardsYouWonToAddToDeck(List<PaperCard> losses) {
        //Out of scope for GameLogicTests.
        return List.of();
    }

    @Override
    public Map<Card, Integer> assignCombatDamage(Card attacker, CardCollectionView blockers, CardCollectionView remaining, int damageDealt, GameEntity defender, boolean overrideOrder) {
        //TODO: Check action queue. Maybe add some sanity checks to ensure assignment is valid?
        //TODO: Banding? Ha ha, what's that?
        //Default behavior, just go down the line and assign lethal.
        //Sampled from VAssignCombatDamage.initialAssignDamage
        int dmgLeft = damageDealt;
        Map<Card, Integer> out = new HashMap<>();
        boolean hasDeathtouch = attacker.hasKeyword(Keyword.DEATHTOUCH);
        Card lastBlocker = null;
        for (Card blocker : blockers) {
            int lethal = blocker.getExcessDamageValue(hasDeathtouch);
            int damage = Math.min(lethal, dmgLeft);
            out.put(blocker, damage);
            dmgLeft -= damage;
            lastBlocker = blocker;
            if(dmgLeft <= 0)
                break;
        }

        if(dmgLeft > 0) {
            if(attacker.hasKeyword(Keyword.TRAMPLE) || blockers.isEmpty())
                out.put(null, dmgLeft);
            else //Pile remaining damage onto the last defender.
                out.put(lastBlocker, out.get(lastBlocker) + dmgLeft);
        }

        return out;
    }

    @Override
    public Map<GameEntity, Integer> divideShield(Card effectSource, Map<GameEntity, Integer> affected, int shieldAmount) {
        //TODO: Check action queue.
        //Maybe should require a decision when shieldAmount < the sum of all max values?
        int amountRemaining = shieldAmount;
        Map<GameEntity, Integer> out = new HashMap<>();
        for (Map.Entry<GameEntity, Integer> item : affected.entrySet()) {
            int maximum = item.getValue();
            int toApply = Math.min(maximum, amountRemaining);
            out.put(item.getKey(), toApply);
            amountRemaining -= toApply;
            if(amountRemaining <= 0)
                break;
        }
        return out;
    }

    @Override
    public Map<Byte, Integer> specifyManaCombo(SpellAbility sa, ColorSet colorSet, int manaAmount, boolean different) {
        //TODO: Check action queue.
        return Map.of(colorSet.iterator().next().getColorMask(), manaAmount);
    }

    private CardCollectionView choosePermanents(int min, int max, CardCollectionView validTargets) {
        if(min >= validTargets.size())
            return validTargets;
        else if(max <= 0 || validTargets.isEmpty())
            return CardCollection.EMPTY;
        //TODO: Check action queue. Maybe sort the validTargets for default behavior.
        return new CardCollection(validTargets.stream().limit(min).toList());
    }

    @Override
    public CardCollectionView choosePermanentsToSacrifice(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return choosePermanents(min, max, validTargets);
    }

    @Override
    public CardCollectionView choosePermanentsToDestroy(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return choosePermanents(min, max, validTargets);
    }

    @Override
    public Integer announceRequirements(SpellAbility ability, int min, int max, String announce) {
        //Mostly used for X-values. AI handles this within the API classes.
        Card host = ability.getHostCard();
        Cost cost = ability.getPayCosts();
        ActionItemChoice.Number choice;
        if("X".equals(announce)) {
            choice = queue.getPendingChoiceOfType(ActionItemChoice.XValue.class, playerIndex);
            if(choice == null)
                throw new GameLogicTestException("Needed an X value for %s, but none was provided.", ability);
            if(cost != null && !cost.isMandatory() && choice.getValue() == null)
                return null; //Could theoretically choose null for non-mandatory costs? Don't think players can normally do this.
        }
        else
            choice = queue.getPendingChoiceOfType(ActionItemChoice.Number.class, playerIndex);

        if(choice == null || choice.getValue() == null)
            throw new GameLogicTestException("Needed a chosen number for %s, but none was provided.", ability);

        int value = choice.getValue();
        if(value < min || value > max)
            throw new GameLogicTestException("Expected choice is out of bounds. Chosen: %d; min: %d; max: %d", value, min, max);

        queue.fulfillCriteria(choice);

        return value;
    }

    @Override
    public TargetChoices chooseNewTargetsFor(SpellAbility ability, Predicate<GameObject> filter, boolean optional) {
        //Copied from PlayerControllerHuman.
        //TODO: Move the non-UI logic to somewhere in the game module.
        final SpellAbility sa = ability.isWrapper() ? ((WrappedAbility) ability).getWrappedAbility() : ability;
        if (!sa.usesTargeting()) {
            return null;
        }
        final TargetChoices oldTarget = sa.getTargets();
        sa.clearTargets();
        if (handleChooseTargets(sa, oldTarget.size(), sa.isDividedAsYouChoose() ? Lists.newArrayList(oldTarget.getDividedValues()) : null, filter, optional, false)) {
            return sa.getTargets();
        } else {
            sa.setTargets(oldTarget);
            // Return old target, since we had to reset them above
            return null;
        }
    }

    @Override
    public boolean chooseTargetsFor(SpellAbility currentAbility) {
        //Copied from PlayerControllerHuman.
        //TODO: Move the non-UI logic to somewhere in the game module.
        boolean canFilterMustTarget = true;

        // Can't filter MustTarget if any parent ability is also targeting
        SpellAbility checkSA = currentAbility.getParent();
        while (checkSA != null) {
            if (checkSA.usesTargeting()) {
                canFilterMustTarget = false;
                break;
            }
            checkSA = checkSA.getParent();
        }
        // Can't filter MustTarget is any SubAbility is also targeting
        checkSA = currentAbility.getSubAbility();
        while (checkSA != null) {
            if (checkSA.usesTargeting()) {
                canFilterMustTarget = false;
                break;
            }
            checkSA = checkSA.getSubAbility();
        }

        boolean result = handleChooseTargets(currentAbility, null, null, null, false, canFilterMustTarget);

        final Iterable<GameEntity> targets = currentAbility.getTargets().getTargetEntities();
        final int size = Iterables.size(targets);
        int amount = currentAbility.getStillToDivide();

        //TODO: Handle divide

        return result;
    }

    @Override
    public Pair<SpellAbilityStackInstance, GameObject> chooseTarget(SpellAbility sa, List<Pair<SpellAbilityStackInstance, GameObject>> allTargets) {
        //TODO: Check action queue.
        //if (allTargets.size() < 2) {
            return Iterables.getFirst(allTargets, null);
        //}
    }

    private boolean handleChooseTargets(SpellAbility ability, Integer numTargets, Collection<Integer> divisionValues, Predicate<GameObject> filter, boolean optional, boolean canFilterMustTarget) {
        //Replica of TargetSelection.chooseTargets.
        //TODO: Find a new home for the logic that checks if it's even possible to choose a valid combination of targets.
        if (!ability.usesTargeting()) {
            throw new RuntimeException("TargetSelection.chooseTargets called for ability that does not target - " + ability);
        }
        final TargetRestrictions tgt = ability.getTargetRestrictions();

        // Number of targets is explicitly set only if spell is being redirected (ex. Swerve or Redirect)
        final int minTargets = numTargets != null ? numTargets : ability.getMinTargets();
        final int maxTargets = numTargets != null ? numTargets : ability.getMaxTargets();
        //final int maxTotalCMC = tgt.getMaxTotalCMC(ability.getHostCard(), ability);
        final int numTargeted = ability.getTargets().size();

        final boolean hasEnoughTargets = minTargets == 0 || numTargeted >= minTargets;
        final boolean hasAllTargets = numTargeted == maxTargets && maxTargets > 0;
        if (maxTargets == 0 && minTargets == 0) { return true; }

        // if not enough targets chosen, cancel Ability
        //TODO: This?
//        if (this.bTargetingDone && !hasEnoughTargets) {
//            return false;
//        }
//
//        if (this.bTargetingDone && hasEnoughTargets || hasAllTargets || ability.isDividedAsYouChoose() && divisionValues == null && ability.getStillToDivide() == 0) {
//            return true;
//        }


        final List<ZoneType> zones = tgt.getZone();
        boolean mandatory = (ability.isTrigger() || tgt.getMandatory()) && !optional;
        if (zones.size() == 1 && zones.get(0) == ZoneType.Stack) {
            // If Zone is Stack, the choices are handled slightly differently.
            // Handle everything inside function due to interaction with StackInstance
            return handleChooseStackTargets(ability, numTargets, mandatory);
        }

        List<GameEntity> candidates = tgt.getAllCandidates(ability, false);
        boolean hasEnoughCandidates = candidates.size() >= minTargets;
        if (tgt.isDifferentControllers() || tgt.isForEachPlayer()) {
            PlayerCollection controllers = new PlayerCollection();
            IterableUtil.filter(candidates, Card.class).forEach(c -> controllers.add(c.getController()));
            hasEnoughCandidates &= controllers.size() >= minTargets;
        }
        mandatory &= hasEnoughCandidates;

        ActionItemChoice.Target targetChoice = queue.getPendingChoiceOfType(ActionItemChoice.Target.class, t -> t.matchesSpellAbility(ability), this.playerIndex);

        if (!hasEnoughCandidates && !hasEnoughTargets) {
            //TODO: Flag a near-miss if there is a target specified?
            // Cancel ability if there aren't any valid Candidates
            return false;
        }
        if ((ability.isTrigger() || tgt.getMandatory()) && candidates.isEmpty() && hasEnoughTargets) {
            // Mandatory target selection, that has no candidates but enough targets (Min == 0, but no choices)
            return true;
        }

        if (tgt.isRandomTarget() && numTargets == null) {
            List<GameEntity> choices = new ArrayList<>();
            // currently, only cards that target randomly use a random number of targets
            int top = Math.min(candidates.size(), maxTargets); // prevents choosing more targets than possible
            int bot = minTargets > 0 ? minTargets : 1; // prevents randomly choosing zero targets
            int num = tgt.isRandomNumTargets() ? Aggregates.randomInt(bot, top) : minTargets;
            for (int i=0; i<num; i++) {
                final GameEntity choice = Aggregates.random(candidates);
                if (choice != null) {
                    choices.add(choice);
                    candidates.remove(choice);
                }
            }
            return ability.getTargets().addAll(choices);
        }

        List<Card> validTargets = CardUtil.getValidCardsToTarget(ability);
        boolean mustTargetFiltered = false;
        if (canFilterMustTarget) {
            mustTargetFiltered = StaticAbilityMustTarget.filterMustTargetCards(this.player, validTargets, ability);
        }
        if (filter != null) {
            validTargets = new CardCollection(IterableUtil.filter(validTargets, filter));
        }

        if (validTargets.isEmpty()) {
            // If all targets are filtered after applying MustTarget static ability, the spell can't be cast or the ability can't be activated
            if (mustTargetFiltered) {
                return false;
            }
            //if no valid cards to target and only one valid non-card, auto-target the non-card
            //this handles "target opponent" cards, along with any other cards that can only target a single non-card game entity
            //note that we don't handle auto-targeting cards this way since it's possible that the result will be undesirable
            if (minTargets != 0) {
                List<GameEntity> nonCardTargets = tgt.getAllCandidates(ability, true);
                if (nonCardTargets.size() == 1) {
                    return ability.getTargets().add(nonCardTargets.get(0));
                }
                if (nonCardTargets.isEmpty()) {
                    return false;
                }
            }
        }
        else if (validTargets.size() == 1 && minTargets != 0 && ability.isTrigger() && !tgt.canTgtPlayer()) {
            //if only one valid target card for triggered ability, auto-target that card
            //only do this for triggered abilities to prevent auto-targeting when user chooses
            //to play a spell or activate an ability
            if (ability.isDividedAsYouChoose()) {
                ability.addDividedAllocation(validTargets.get(0), ability.getStillToDivide());
            }
            return ability.getTargets().add(validTargets.get(0));
        }

        if(targetChoice == null) {
            if(ability.isTrigger()) //If we don't have a target now, but there's one in a future criteria block, then we've failed some criteria that we needed to resolve before we got here.
                queue.lookAheadAndFailIfChoiceOfType(ActionItemChoice.Target.class, t -> t.matchesSpellAbility(ability), playerIndex);
            if(mandatory)
                throw new GameLogicTestException("Needed targets for %s, but none were assigned.", ability);
            return false;
        }

        List<GameEntity> targets = targetChoice.getResolvedTargets(getGame());
        List<GameEntity> failedTargets = targets.stream().filter(t -> !ability.canTarget(t)).toList();
        if(!failedTargets.isEmpty())
            throw new GameLogicTestException("Failed to assign targets for %s: %s", ability, failedTargets);
        ability.getTargets().addAll(targets);
        assert(candidates.containsAll(targets));
        //TODO: Verify min and max targets
        queue.fulfillCriteria(targetChoice);
        this.log("Assigned targets for %s: %s", ability, targets);
        //TODO: Be sure to handle divisionValues
        return true;

    }

    private boolean handleChooseStackTargets(SpellAbility ability, Integer numTargets, boolean mandatory) {
        List<SpellAbility> candidates = getGame().getStack().stream()
                .map(SpellAbilityStackInstance::getSpellAbility)
                .filter(ability::canTargetSpellAbility)
                .toList();
        ActionItemChoice.Target targetChoice = queue.getPendingChoiceOfType(ActionItemChoice.Target.class, t -> t.matchesSpellAbility(ability), this.playerIndex);
        if(targetChoice == null) {
            if(mandatory)
                throw new GameLogicTestException("Needed targets for %s, but none were assigned.", ability);
            return false;
        }
        List<GameEntity> targets = targetChoice.getResolvedTargets(getGame());
        //TODO: Disambiguate multiple abilities from a single host card. Guess resolved targets needs to be able to return SA's too?
        Set<SpellAbility> targetSAs = new HashSet<>(targets.size());
        getSAForTarget: for(GameEntity target : targets) {
            for(SpellAbility candidate : candidates) {
                if (candidate.getHostCard() == target && !targetSAs.contains(candidate)) {
                    targetSAs.add(candidate);
                    continue getSAForTarget;
                }
            }
            throw new GameLogicTestException("Error assigning target for %s - Unable to target any SpellAbility on Stack from %s", ability, target);
        }
        ability.getTargets().addAll(targetSAs);
        queue.fulfillCriteria(targetChoice);
        this.log("Assigned targets for %s: %s", ability, targetSAs);
        return true;
    }

    @Override
    public boolean helpPayForAssistSpell(ManaCostBeingPaid cost, SpellAbility sa, int max, int requested) {
        //TODO: Check action queue.
        return true;
    }

    @Override
    public Player choosePlayerToAssistPayment(FCollectionView<Player> optionList, SpellAbility sa, String title, int max) {
        return chooseSingleEntityForEffect(optionList, null, sa, title, true, null, null);
    }

    @Override
    public CardCollectionView chooseCardsForEffect(CardCollectionView sourceList, SpellAbility sa, String title, int min, int max, boolean isOptional, Map<String, Object> params) {
        //TODO: Check action queue.
        if(isOptional)
            return new CardCollection();
        return new CardCollection(Iterables.limit(sourceList, min));
    }

    @Override
    public CardCollection chooseCardsForEffectMultiple(Map<String, CardCollection> validMap, SpellAbility sa, String title, boolean isOptional) {
        //TODO: Check action queue. Might be a bit fiddley.
        if(isOptional)
            return new CardCollection();
        CardCollection out = new CardCollection();
        for(CardCollection c : validMap.values())
            if(!c.isEmpty())
                out.add(c.getFirst());
        return out;
    }

    @Override
    public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> optionList, DelayedReveal delayedReveal, SpellAbility sa, String title, boolean isOptional, Player relatedPlayer, Map<String, Object> params) {
        //TODO: Check action queue.
        if(isOptional || optionList.isEmpty())
            return null;
        return optionList.getFirst();
    }

    @Override
    public <T extends GameEntity> List<T> chooseEntitiesForEffect(FCollectionView<T> optionList, int min, int max, DelayedReveal delayedReveal, SpellAbility sa, String title, Player relatedPlayer, Map<String, Object> params) {
        //TODO: Check action queue.
        return Lists.newArrayList(Iterables.limit(optionList, min));
    }

    @Override
    public List<SpellAbility> chooseSpellAbilitiesForEffect(List<SpellAbility> spells, SpellAbility sa, String title, int num, Map<String, Object> params) {
        //TODO: Check action queue.
        return Lists.newArrayList(Iterables.limit(spells, num));
    }

    @Override
    public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells, SpellAbility sa, String title, Map<String, Object> params) {
        //TODO: Check action queue.
        //if (spells.size() < 2) {
            return Iterables.getFirst(spells, null);
        //}
    }

    @Override
    public boolean confirmAction(SpellAbility sa, PlayerActionConfirmMode mode, String message, List<String> options, Card cardToShow, Map<String, Object> params) {
        //TODO: Check action queue. Also examine mode.
        //PlayerActionConfirmMode.ChangeZoneToAltDestination - usually true
        return true;
    }

    @Override
    public boolean confirmBidAction(SpellAbility sa, PlayerActionConfirmMode bidlife, String string, int bid, Player winner) {
        //TODO: Check action queue.
        return false;
    }

    @Override
    public boolean confirmReplacementEffect(ReplacementEffect replacementEffect, SpellAbility effectSA, GameEntity affected, String question) {
        //TODO: Check action queue.
        return true;
    }

    @Override
    public boolean confirmStaticApplication(Card hostCard, PlayerActionConfirmMode mode, String message, String logic) {
        //TODO: Check action queue. (Though this one's almost always desirable.)
        return true;
    }

    @Override
    public boolean confirmTrigger(WrappedAbility sa) {
        //TODO: Check action queue.
        return true;
    }

    @Override
    public List<Card> exertAttackers(List<Card> attackers) {
        //TODO: Check action queue.
        return List.of();
    }

    @Override
    public List<Card> enlistAttackers(List<Card> attackers) {
        //TODO: Check action queue.
        return List.of();
    }

    @Override
    public void declareAttackers(Player attacker, Combat combat) {
        //TODO: Check action queue.
        //Auto-handle must-attacks.
        for(Card c : attacker.getCreaturesInPlay()) {
            final List<GameEntity> mustAttack = StaticAbilityMustAttack.entitiesMustAttack(c);
            if (!mustAttack.isEmpty()) {
                for (final GameEntity defender : mustAttack) {
                    if (combat.getDefenders().contains(defender) && CombatUtil.canAttack(c, defender)) {
                        combat.addAttacker(c, defender);
                        break;
                    }
                }
            }
        }
        if(!CombatUtil.validateAttackers(combat))
            throw new RuntimeException("Invalid attack declaration!");
    }

    @Override
    public void declareBlockers(Player defender, Combat combat) {
        //TODO: Check action queue.
        String err = CombatUtil.validateBlocks(combat, defender);
        if(err != null)
            throw new RuntimeException("Invalid block declaration! " + err);
    }

    @Override
    public CardCollection orderBlockers(Card attacker, CardCollection blockers) {
        //TODO: Check action queue.
        return blockers;
    }

    @Override
    public CardCollection orderBlocker(Card attacker, Card blocker, CardCollection oldBlockers) {
        //TODO: Check action queue.
        CardCollection out = new CardCollection(blocker);
        out.addAll(oldBlockers);
        return out;
    }

    @Override
    public CardCollection orderAttackers(Card blocker, CardCollection attackers) {
        //TODO: Check action queue.
        return attackers;
    }

    @Override
    public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String messagePrefix, boolean addMsgSuffix) {
        //TODO: Check action queue (for asserts).
        System.out.printf("Revealed: [%s]; owner: %s; zone: %s%n", cards, owner, zone);
    }

    @Override
    public void reveal(List<CardView> cards, ZoneType zone, PlayerView owner, String messagePrefix, boolean addMsgSuffix) {
        //TODO: Check action queue (for asserts).
        System.out.printf("Revealed: [%s]; owner: %s; zone: %s%n", cards, owner, zone);
    }

    @Override
    public void notifyOfValue(SpellAbility saSource, GameObject realtedTarget, String value) {
        //TODO: Check action queue (for asserts).
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForScry(CardCollection topN) {
        //TODO: Check action queue.
        return new ImmutablePair<>(topN, new CardCollection());
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(CardCollection topN) {
        //TODO: Check action queue.
        return new ImmutablePair<>(topN, new CardCollection());
    }

    @Override
    public boolean willPutCardOnTop(Card c) {
        //TODO: Check action queue. True for top, false for bottom.
        return true;
    }

    @Override
    public CardCollectionView orderMoveToZoneList(CardCollectionView cards, ZoneType destinationZone, SpellAbility source) {
        //TODO: Check action queue.
        //Maybe reverse input if moving to library?
        return cards;
    }

    @Override
    public CardCollection chooseCardsToDiscardFrom(Player playerDiscard, SpellAbility sa, CardCollection validCards, int min, int max, CardCollectionView visibleToChooser) {
        //TODO: Check action queue.
        return new CardCollection(Iterables.limit(validCards, min));
    }

    @Override
    public CardCollectionView chooseCardsToDiscardUnlessType(int min, CardCollectionView hand, String[] unlessTypes, SpellAbility sa) {
        //TODO: Check action queue.
        for(Card c : hand) {
            for(String uType : unlessTypes)
                if(c.isValid(uType, sa.getActivatingPlayer(), sa.getHostCard(), sa))
                    return new CardCollection(c);
        }
        return new CardCollection(Iterables.limit(hand, min));
    }

    @Override
    public CardCollectionView chooseCardsToDiscardToMaximumHandSize(int numDiscard) {
        return chooseCardsToDiscardFrom(player, null, new CardCollection(player.getCardsIn(ZoneType.Hand)), numDiscard, numDiscard);
    }

    @Override
    public CardCollectionView chooseCardsToDelve(int genericAmount, CardCollection grave) {
        //TODO: Check action queue.
        //(Each card exiled from GY while casting pays for 1)
        return new CardCollection(Iterables.limit(grave, genericAmount));
    }

    @Override
    public Map<Card, ManaCostShard> chooseCardsForConvokeOrImprovise(SpellAbility sa, ManaCost manaCost, CardCollectionView untappedCards, boolean artifacts, boolean creatures, Integer maxReduction) {
        //TODO: Check action queue.
        //This one will be weird. Multicolor cards used for convoke also need a choice of which mana type.
        //Consult InputSelectCardsForConvokeOrImprovise.onCardSelected
        return Map.of();
    }

    @Override
    public List<Card> chooseCardsForSplice(SpellAbility sa, List<Card> cards) {
        //TODO: Check action queue.
        return List.of();
    }

    @Override
    public CardCollectionView chooseCardsToRevealFromHand(int min, int max, CardCollectionView valid) {
        //TODO: Check action queue.
        return new CardCollection(Iterables.limit(valid, min));
    }

    @Override
    public List<SpellAbility> chooseSaToActivateFromOpeningHand(List<SpellAbility> usableFromOpeningHand) {
        //TODO: Check action queue?
        //If the card was deliberately put there and we aren't skipping the start of the game, we probably want to use it.
        return usableFromOpeningHand;
    }

    @Override
    public Player chooseStartingPlayer(boolean isFirstGame) {
        //We assign this externally anyway.
        return player;
    }

    @Override
    public PlayerZone chooseStartingHand(List<PlayerZone> zones) {
        //TODO: Check action queue.
        return zones.get(0);
    }

    @Override
    public Mana chooseManaFromPool(List<Mana> manaChoices) {
        //Not currently used by human players, and AI just chooses the first one.
        //TODO: Cleanup?
        return manaChoices.get(0);
    }

    @Override
    public String chooseSomeType(String kindOfType, SpellAbility sa, Collection<String> validTypes, boolean isOptional) {
        //TODO: Check action queue.
        if(isOptional)
            return null;
        return validTypes.stream().findFirst().orElse(null);
    }

    @Override
    public String chooseSector(Card assignee, String ai, List<String> sectors) {
        //TODO: Check action queue?
        return sectors.stream().findFirst().orElseThrow();
    }

    @Override
    public List<Card> chooseContraptionsToCrank(List<Card> contraptions) {
        //TODO: Check action queue?
        return contraptions;
    }

    @Override
    public int chooseSprocket(Card assignee, List<Integer> sprockets) {
        //TODO: Check action queue?
        return sprockets.get(0);
    }

    @Override
    public PlanarDice choosePDRollToIgnore(List<PlanarDice> rolls) {
        //TODO: Check action queue.
        return rolls.contains(PlanarDice.Blank) ? PlanarDice.Blank : rolls.get(0);
    }

    @Override
    public Integer chooseRollToIgnore(List<Integer> rolls) {
        //TODO: Check action queue.
        return rolls.stream().sorted().findFirst().orElseThrow();
    }

    @Override
    public List<Integer> chooseDiceToReroll(List<Integer> rolls) {
        //TODO: Check action queue.
        return List.of();
    }

    @Override
    public Integer chooseRollToModify(List<Integer> rolls) {
        //TODO: Check action queue.
        return null;
    }

    @Override
    public RollDiceEffect.DieRollResult chooseRollToSwap(List<RollDiceEffect.DieRollResult> rolls) {
        //TODO: Check action queue.
        return null;
    }

    @Override
    public String chooseRollSwapValue(List<String> swapChoices, Integer currentResult, int power, int toughness) {
        //TODO: Check action queue.
        return null;
    }

    @Override
    public Object vote(SpellAbility sa, String prompt, List<Object> options, ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
        //TODO: Check action queue. Maybe some easy way to do all the votes at once?
        return options.get(0);
    }

    @Override
    public boolean mulliganKeepHand(Player player, int cardsToReturn) {
        //TODO: Check action queue?
        return true;
    }

    @Override
    public CardCollectionView tuckCardsViaMulligan(CardCollectionView hand, int cardsToReturn) {
        //TODO: Check action queue?
        return new CardCollection(Iterables.limit(hand, cardsToReturn));
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        //This is the big "you have priority, do what you want" method.
        ActionItemPriority action = queue.peekPriority();
        Game game = this.getGame();

        queue.assertNoPartiallyFinishedEvents();

        if(action == null) {
            if(!game.getStack().isEmpty()) {
                queue.runAsserts(game, false);
                return null;
            }
            queue.advancePriority(game);
            game.setGameOver(GameEndReason.Draw);
            return null;
        }

        if(!action.isReady(this)) {
            queue.assertNoTimeout(game);
            queue.runAsserts(game, false);
            return null;
        }

        queue.advancePriority(game);

        //TODO: This kinda probably shouldn't return a list.
        //Only apparent use case is so human players can shift-click a stack of lands to tap them all at once.
        //Return null to pass priority.
        return List.of(action.getSpellAbility(game));
    }

    @Override
    public boolean playChosenSpellAbility(SpellAbility sa) {
        this.log("Playing SpellAbility %s", sa);
        //Exists exclusively for the AI's sake, but we can use it to check if our chosen SA was successfully played.
        boolean playResult = PlaySpellAbility.playSpellAbility(this, player, sa);
        if(!playResult) {
            ActionItemPriority lastPriority = this.queue.currentPriority();
            if(sa == lastPriority.getSpellAbility(this.getGame()))
                throw new GameLogicTestException("Failed to play chosen SpellAbility - %s %s", sa, lastPriority);
            //Would be nice to know *why* it failed, but that's true for human players too.
        }
        else
            this.log("Played %s", sa);
        return playResult;
    }

    @Override
    public List<AbilitySub> chooseModeForAbility(SpellAbility sa, List<AbilitySub> possible, int min, int max, boolean allowRepeat) {
        if (!allowRepeat && min == max && max == possible.size()) {
            return possible;
        }
        List<AbilitySub> available = Lists.newArrayList(possible);
        List<AbilitySub> chosen = Lists.newArrayListWithCapacity(max);
        for (int i = 0; i < max && !available.isEmpty(); i++) {
            //TODO: Check action queue.
            //TODO: Handle pawprints.
            AbilitySub selected = available.get(0);
            chosen.add(selected);
            if(!allowRepeat)
                available.remove(0);
        }
        return chosen;
    }

    @Override
    public int chooseNumberForCostReduction(SpellAbility sa, int min, int max) {
        //Only used for full control.
        return max;
    }

    @Override
    public int chooseNumberForKeywordCost(SpellAbility sa, Cost cost, KeywordInterface keyword, String prompt, int max) {
//        if (max <= 0) {
//            return 0;
//        }
        //TODO: Check action queue.
        return 0;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String title, int min, int max) {
        //TODO: Check action queue.
        return min;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String title, List<Integer> values, Player relatedPlayer) {
        //TODO: Check action queue.
        return values.get(0);
    }

    @Override
    public boolean chooseBinary(SpellAbility sa, String question, BinaryChoiceType kindOfChoice, Boolean defaultChoice) {
        //TODO: Check action queue.
        return false;
    }

    @Override
    public boolean chooseFlipResult(SpellAbility sa, Player flipper, boolean call) {
        //TODO: Reevaluate this; it's been updated.
        //Used for Krark's Thumb, choosing which coin to accept.
        //Only ever called if there's more than one option, which means there'd be exactly two options, which means results is kinda redundant.
        //Call is also only ever true. This could use some cleanup.
        //TODO: Check action queue?
        return true;
    }

    @Override
    public byte chooseColor(String message, SpellAbility sa, ColorSet colors) {
        //TODO: Check action queue.
        return colors.iterator().next().getColorMask();
    }

    @Override
    public byte chooseColorAllowColorless(String message, Card c, ColorSet colors) {
        //TODO: Check action queue.
        return MagicColor.COLORLESS;
    }

    @Override
    public ColorSet chooseColors(String message, SpellAbility sa, int min, int max, ColorSet options) {
        //TODO: Check action queue.
        return ColorSet.fromEnums(Iterables.limit(options, min));
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, String message, Predicate<ICardFace> cpp, String name) {
        //TODO: This has a *wildly* different code path from its overload. This one's currently only used by human players. Need to clean that up.
        throw new UnsupportedOperationException("Should not be called for non-humans");
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, List<ICardFace> faces, String message) {
        //TODO: Check action queue.
        return Iterables.getFirst(faces, null);
    }

    @Override
    public CardState chooseSingleCardState(SpellAbility sa, List<CardState> states, String message, Map<String, Object> params) {
        //TODO: Check action queue.
        return Iterables.getFirst(states, null);
    }

    @Override
    public boolean chooseCardsPile(SpellAbility sa, CardCollectionView pile1, CardCollectionView pile2, String faceUp) {
        //TODO: Check action queue.
        //True = pile 1
        return true;
    }

    @Override
    public CounterType chooseCounterType(List<CounterType> options, SpellAbility sa, String prompt, Map<String, Object> params) {
        //TODO: Check action queue.
        return Iterables.getFirst(options, null);
    }

    @Override
    public String chooseKeywordForPump(List<String> options, SpellAbility sa, String prompt, Card tgtCard) {
        //TODO: Check action queue.
        return Iterables.getFirst(options, null);
    }

    @Override
    public boolean confirmPayment(CostPart costPart, String string, SpellAbility sa) {
        //TODO: Migrate the logic for determining the string component over to the UI.
        //The one in HumanCostDecision can probably just inline the use of confirmPayment...
        //TODO: Check action queue.
        return true;
    }

    @Override
    public ReplacementEffect chooseSingleReplacementEffect(List<ReplacementEffect> possibleReplacers) {
        //TODO: Check action queue.
        return Iterables.getFirst(possibleReplacers, null);
    }

    @Override
    public StaticAbility chooseSingleStaticAbility(List<StaticAbility> possibleReplacers) {
        //TODO: Check action queue? Usually only used for full control.
        return Iterables.getFirst(possibleReplacers, null);
    }

    @Override
    public String chooseProtectionType(SpellAbility sa, List<String> choices) {
        //TODO: Check action queue.
        return Iterables.getFirst(choices, null);
    }

    @Override
    public void revealAnte(String message, Multimap<Player, PaperCard> removedAnteCards) {
        //Don't care.
    }

    @Override
    public void revealAISkipCards(String message, Map<Player, Map<DeckSection, List<? extends PaperCard>>> deckCards) {
        //Not our problem.
    }

    @Override
    public void revealUnsupported(Map<Player, List<PaperCard>> unsupported) {
        if(!unsupported.isEmpty()) //Probably shouldn't ever happen?
            throw new RuntimeException("Unsupported cards? - " + unsupported);
    }

    @Override
    public void resetAtEndOfTurn() {
        //Only used by AI.
    }

    @Override
    public List<OptionalCostValue> chooseOptionalCosts(SpellAbility choosen, List<OptionalCostValue> optionalCostValues) {
        //TODO: Check action queue.
        return List.of();
    }

    @Override
    public List<CostPart> orderCosts(List<CostPart> costs) {
        //TODO: Check action queue? Only used for full control.
        return costs;
    }

    @Override
    public boolean payCostToPreventEffect(Cost cost, SpellAbility sa, boolean alreadyPaid, FCollectionView<Player> allPayers) {
        return PlaySpellAbility.payCostDuringAbilityResolve(this, player, cost, sa, null);
    }

    @Override
    public boolean payCostDuringRoll(Cost cost, SpellAbility sa) {
        return PlaySpellAbility.payCostDuringAbilityResolve(this, player, cost, sa, null);
    }

    @Override
    public boolean payCombatCost(Card card, Cost cost, SpellAbility sa, String prompt) {
        if(cost.isOnlyManaCost() && cost.getTotalMana().isZero())
            return true;
        return PlaySpellAbility.payCostDuringAbilityResolve(this, player, cost, sa, prompt);
    }

    @Override
    public boolean payManaCost(ManaCost toPay, CostPartMana costPartMana, SpellAbility sa, String prompt, ManaConversionMatrix matrix, boolean effect) {
        return PlaySpellAbility.payManaCost(this, toPay, costPartMana, sa, player, prompt, matrix, effect);
    }

    @Override
    public CardCollectionView chooseCardsForCost(CardCollectionView optionList, SpellAbility sa, CostPartWithList cpl, int amount, boolean isOptional, String prompt) {
        return new CardCollection(Iterables.limit(optionList, amount));
    }

    private static final Comparator<ManaCostShard> STRICTEST_FIRST = Comparator.comparing(ManaCostShard::isGeneric).thenComparing(ManaCostShard::isMultiColor);

    @Override
    public boolean applyManaToCost(ManaCostBeingPaid toPay, SpellAbility ability, String prompt, ManaConversionMatrix matrix, boolean effect) {
        //TODO: Untangle this mess.
        //TODO: Check action queue for payment choice nodes.
        Set<Card> manaSources = this.player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(Card::isBasicLand) //TODO: Support auto-pay with nonbasic mana sources? Need to know which ones aren't needed later in the queue though.
                .filter(Card::isUntapped) //Maybe sort colorless sources first?
                .collect(Collectors.toCollection(HashSet::new));

        Multimap<MagicColor.Color, SpellAbility> colorToAbility = MultimapBuilder.enumKeys(MagicColor.Color.class).hashSetValues().build();
        for(Card c : manaSources) {
            for(SpellAbility manaAbility : c.getManaAbilities())
            {
                Arrays.stream(manaAbility.getManaPart().mana(manaAbility) //Could possibly use this colors-produced map elsewhere.
                        .split(" "))
                        .map(MagicColor::fromName)
                        .map(MagicColor.Color::fromByte)
                        .distinct()
                        .forEach(color -> colorToAbility.put(color, manaAbility));
            }
        }

        List<Mana> manaSpentToPay = ability.getPayingMana();
        ManaPool manaPool = player.getManaPool();

        while(!toPay.isPaid()) {
            //Try spending anything we have in the mana pool.
            poolLoop: while (!toPay.isPaid() && !manaPool.isEmpty()) {
                for (byte color : ManaAtom.MANATYPES) {
                    if (manaPool.tryPayCostWithColor(color, ability, toPay, manaSpentToPay)) {
                        this.log("     (Spending %s from pool)", MagicColor.toShortString(color));
                        continue poolLoop;
                    }
                }
                break;
            }
            if (toPay.isPaid()) {
                break;
            }

            ManaCostShard nextShard = toPay.getUnpaidShards().stream().min(STRICTEST_FIRST).orElseThrow();
            SpellAbility chosenSA = null;

            manaAbilityLoop: for (MagicColor.Color shardColor : nextShard.isGeneric() ? Set.of(MagicColor.Color.values()) : nextShard.getColor()) { //Try all colors for hybrid shards. TODO: constant set.
                for (SpellAbility sa : colorToAbility.get(shardColor)) {
                    sa.setActivatingPlayer(player);
                    if(sa.canPlay()) {
                        chosenSA = sa;
                        break manaAbilityLoop;
                    }
                }
            }
            if(chosenSA == null) {
                //Could test for phyrexian here but that probably makes more sense for the test to specify explicitly.
                throw new GameLogicTestException("Unable to find a basic mana ability to pay cost '%s'. SA: %s", toPay, ability);
            }
            //As long as this only uses basic lands, shouldn't need to worry about the ability having any costs or complexities to it.

            if(!PlaySpellAbility.playSpellAbility(this, player, chosenSA))
                throw new GameLogicTestException("Failed to activate mana ability - %s", chosenSA);
            this.log("     (Activating %s for mana)", chosenSA);

            manaPool.payManaFromAbility(ability, toPay, chosenSA);
        }

        //player.getManaPool().tryPayCostWithColor(colorCode, saPaidFor, manaCost, saPaidFor.getPayingMana())
        return toPay.isPaid();
    }

    @Override
    public CostDecisionMakerBase getCostDecisionMaker(Player player, SpellAbility ability, boolean effect, String prompt) {
        return new GameLogicTestCostDecisionMaker(this, player, ability, effect);
    }

    @Override
    public String chooseCardName(SpellAbility sa, Predicate<ICardFace> cpp, String valid, String message) {
        //TODO: Require action queue.
        return "Forest";
    }

    @Override
    public String chooseCardName(SpellAbility sa, List<ICardFace> faces, String message) {
        if(faces.isEmpty())
            return "";
        return faces.get(0).getName();
    }

    @Override
    public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, DelayedReveal delayedReveal, String selectPrompt, boolean isOptional, Player decider) {
        return chooseSingleEntityForEffect(fetchList, delayedReveal, sa, selectPrompt, isOptional, decider, null);
    }

    @Override
    public List<Card> chooseCardsForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, int min, int max, DelayedReveal delayedReveal, String selectPrompt, Player decider) {
        return chooseEntitiesForEffect(fetchList, min, max, delayedReveal, sa, selectPrompt, decider, null);
    }

    @Override
    public void autoPassCancel() {
        //No-op
    }

    @Override
    public void awaitNextInput() {
        //No-op
    }

    @Override
    public void cancelAwaitNextInput() {
        //No-op
    }
}
