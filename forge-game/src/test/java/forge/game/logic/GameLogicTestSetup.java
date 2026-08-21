package forge.game.logic;

import forge.game.GameRules;
import forge.game.GameState;
import forge.game.GameType;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.*;

public class GameLogicTestSetup extends GameState {
    public final PlayerSetup player;
    public final PlayerSetup opponent;

    /* package */ final CardReference.ReferencePool referencePool;

    public static class PlayerSetup extends GameState.PlayerState {
        public final int index;
        private int id;
        private final CardReference.ReferencePool referencePool;
        protected final EnumMap<ZoneType, List<CardReference>> explicitCardLists = new EnumMap<>(ZoneType.class);
        protected List<CardReference> explicitLands = null;
        protected boolean padDeck = true; //Whether the bottom of this player's deck gets padded with 10 arbitrary cards.
        /* package */ PlayerSetup(int index, CardReference.ReferencePool referencePool) {
            this.index = index;
            this.referencePool = referencePool;
            this.life = 20;
        }

        public PlayerSetup life(int life) {
            this.life = life;
            return this;
        }

        public PlayerSetup battlefield(String... cardReferences) {
            return this.zone(ZoneType.Battlefield, cardReferences);
        }

        public PlayerSetup zone(ZoneType zone, String... cardReferences) {
            List<CardReference> refs = Arrays.stream(cardReferences).map(referencePool::getCard).toList();
            for(CardReference c : refs) {
                c.setInferredOwner(this.index);
                c.setInferredZone(zone);
                c.setExplicitlyPlaced();
            }
            this.explicitCardLists.put(zone, refs);
            return this;
        }

        /**
         * Sets which lands this player initially has in the battlefield.
         * This will prevent this player's lands from being inferred and generated based on action costs. To retain this
         * behavior, add the lands to the player's battlefield via the .battlefield method.
         */
        public PlayerSetup lands(String... landReferences) {
            this.explicitLands = Arrays.stream(landReferences).map(referencePool::getCard).toList();
            for(CardReference c : explicitLands) {
                c.setInferredOwner(this.index);
                c.setInferredZone(ZoneType.Battlefield);
                c.setExplicitlyPlaced();
            }
            return this;
        }

        public String getName() {
            return "Player " + (this.index + 1);
        }

        /* package */ int getID() {
            return this.id;
        }

        private void putResolvedCardLists(Map<ZoneType, List<CardReference>> nonLands, List<CardReference> lands, CardReference deckPlaceholders) {
            Set<ZoneType> allZones = explicitCardLists.isEmpty() ? EnumSet.noneOf(ZoneType.class) : EnumSet.copyOf(explicitCardLists.keySet());
            if(nonLands != null)
                allZones.addAll(nonLands.keySet());
            for(ZoneType zone : allZones) {
                List<CardReference> cards = explicitCardLists.containsKey(zone) ? explicitCardLists.get(zone) : nonLands.get(zone);

                List<String> zoneText = cards.stream().map(CardReference::getGameStateString).toList();
                this.cardTexts.put(zone, String.join(";", zoneText));
            }

            List<CardReference> landList = this.explicitLands != null ? this.explicitLands : lands;

            if(landList != null && !landList.isEmpty()) {
                List<String> landText = landList.stream().map(CardReference::getGameStateString).toList();
                String newBattlefield = String.join(";", landText);
                if(cardTexts.containsKey(ZoneType.Battlefield) && !cardTexts.get(ZoneType.Battlefield).isEmpty())
                    newBattlefield = this.cardTexts.get(ZoneType.Battlefield) + ";" + newBattlefield;
                this.cardTexts.put(ZoneType.Battlefield, newBattlefield);
            }

            if(deckPlaceholders != null) {
                String newDeck = deckPlaceholders.getGameStateString();
                if(cardTexts.containsKey(ZoneType.Library) && !cardTexts.get(ZoneType.Library).isEmpty())
                    newDeck = cardTexts.get(ZoneType.Library) + ";" + newDeck;
                this.cardTexts.put(ZoneType.Library, newDeck);
            }
        }
    }

    /* package */ GameLogicTestSetup(CardReference.ReferencePool referencePool) {
        this.referencePool = referencePool;
        this.player = new PlayerSetup(0, referencePool);
        this.opponent = new PlayerSetup(1, referencePool);

        this.playerStates.add(player);
        this.playerStates.add(opponent);

        this.tChangePlayer = "p0";
    }

    public GameLogicTestSetup activePlayer(PlayerSetup activePlayer) {
        this.tChangePlayer = "p" + activePlayer.index;
        return this;
    }

    public GameLogicTestSetup turnPhase(PhaseType phase) {
        this.tChangePhase = phase.toString();
        return this;
    }

    @Override
    protected PlayerSetup getPlayerState(int index) {
        return (PlayerSetup) super.getPlayerState(index);
    }

    @Override
    protected PlayerSetup createPlayerState(int index) {
        return new PlayerSetup(index, this.referencePool);
    }

    protected List<PlayerSetup> getPlayerSetups() {
        return this.playerStates.stream().map(PlayerSetup.class::cast).toList();
    }

    @Override
    protected boolean useActualCardID() {
        return true;
    }

    /* package */ GameRules getGameRules() {
        GameRules out = new GameRules(GameType.Constructed);
        out.setPlayForAnte(false);
        out.setManaBurn(false);
        out.setOrderCombatants(false);
        out.setAllowCheatShuffle(false);
        out.setGamesPerMatch(1);
        return out;
    }

    /* package */ void initFromQueue(GameLogicTestActionQueue queue) {
        ActionItemPriority lastPriority = null;
        SpellAbility focusSpellAbility = null;
        Map<Integer, List<Cost>> costsPerPlayer = new HashMap<>(4); //TODO: Track max mana per type per turn when operating across multiple turns?
        Cost lastPriorityCost = null;

        queue.queueState = GameLogicTestActionQueue.ActionQueueState.INITIALIZING;

        //Part 1 - Iterate over the queue and fill in missing info for the card references.
        for (GameLogicTestActionQueue.ActionItem item : queue.queue) {
            if (item instanceof ActionItemPriority priority) {
                if (lastPriorityCost != null) {
                    costsPerPlayer.computeIfAbsent(lastPriority.getPlayerIndex(), ArrayList::new).add(lastPriorityCost);
                    lastPriorityCost = null;
                }

                lastPriority = priority;

                if (priority.subject != null) {
                    CardReference focusCardRef = priority.subject;
                    focusSpellAbility = priority.getSpellAbility();

                    focusCardRef.setInferredOwner(priority.getPlayerIndex());
                    if (focusSpellAbility != null) {
                        ZoneType cardZone = focusSpellAbility.getRestrictions().getZone();
                        if (cardZone == null) {
                            //Shouldn't happen? Warn?
                            cardZone = ZoneType.Battlefield;
                        }
                        focusCardRef.setInferredZone(cardZone);

                        lastPriorityCost = priority.getEstimatedCost();
                    }
                } else
                    focusSpellAbility = null;
            } else {
                if (item instanceof GameLogicTestActionQueue.HasImplicitSetup i) {
                    i.doImplicitSetup(lastPriority, focusSpellAbility);
                }
                if (item instanceof GameLogicTestActionQueue.HasCostAdjustment i && lastPriorityCost != null) {
                    lastPriorityCost = i.adjustEstimatedCost(lastPriority, lastPriorityCost);
                }
                if (item instanceof GameLogicTestActionQueue.HasFocusAdjustment i) {
                    focusSpellAbility = i.getSpellAbility();
                }
            }
        }

        if(lastPriorityCost != null) {
            costsPerPlayer.computeIfAbsent(lastPriority.getPlayerIndex(), ArrayList::new).add(lastPriorityCost);
        }

        //Part 2 - Sum up the needed costs, and convert them to needed lands.
        Map<Integer, List<CardReference>> playerLands = new HashMap<>(4);

        for(Map.Entry<Integer, List<Cost>> e : costsPerPlayer.entrySet()) {
            PlayerSetup player = getPlayerState(e.getKey());
            if(player.explicitLands != null)
                continue; //No need to compute costs for them; their lands were explicitly set.

            Cost totalManaCost = e.getValue().stream().reduce(Cost::add).orElse(Cost.Zero);
            Map<String, Integer> landCounts = GameLogicTestUtils.getLandsForCost(totalManaCost.getTotalMana());
            if(landCounts.isEmpty())
                continue;
            List<CardReference> lands = referencePool.loadLands(player.index, landCounts);
            for(CardReference land : lands)
                land.setExplicitlyPlaced(); //Leave these out of cardsPerZonePerPlayer.
            playerLands.put(player.index, lands);
        }

        //TODO: Infer starting phase/turn.
        if("NONE".equals(tChangePhase))
            tChangePhase = "MAIN1";

        //Assign cards to players.
        Map<Integer, Map<ZoneType, List<CardReference>>> cardsPerZonePerPlayer = new HashMap<>(4);

        for(CardReference ref : this.referencePool.getAllReferences()) {
            if(ref.isExplicitlyPlaced())
                continue; //Cards are already defined in a zone or in playerLands.
            int ownerIndex = ref.ownerIndex;
            if(ownerIndex < 0)
                throw new AssertionError("Unknown owner for card reference - " + ref);
            ZoneType zone = ref.zone;
            if(zone == null)
                throw new AssertionError("Unknown zone for card reference - " + ref);
            cardsPerZonePerPlayer.computeIfAbsent(ownerIndex, i -> new HashMap<>())
                    .computeIfAbsent(zone, z -> new ArrayList<>())
                    .add(ref);
        }

        for(PlayerReference ref : this.referencePool.getAllPlayerReferences()) {
            PlayerSetup player = this.getPlayerState(ref.playerIndex);
            player.id = ref.getID();
            Map<ZoneType, List<CardReference>> nonLands = cardsPerZonePerPlayer.getOrDefault(player.index, Map.of());
            List<CardReference> lands = playerLands.get(player.index);
            CardReference placeholders = player.padDeck ? referencePool.loadDeckPlaceholders(player.index) : null;
            player.putResolvedCardLists(nonLands, lands, placeholders);
        }
    }
}
