package forge.game.logic;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.IIdentifiable;
import forge.game.card.Card;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardReference extends GameLogicTestReference {
    static final Pattern IMPLICIT_REF_PATTERN = Pattern.compile("^\\s*(?:\\[(?<modifier>[^]]+)]\\s*)?(?<name>[^\\[\\]]+)$");
    static final Pattern MODIFIER_PATTERN = Pattern.compile("^\\s*(?:(?<owner>.+?)'s\\s*)?(?:#(?<index>\\d+)|(?<quantity>\\d+)x)?\\s*$");
    protected final String refString;
    public final String cardName;
    protected final int quantity;
    protected final int referenceIndex;

    protected int ownerIndex = -1;
    protected ZoneType zone;

    private PaperCard card;

    protected static int ownerTextToIndex(String ownerText) {
        if (ownerText == null || ownerText.isBlank())
            return 0;
        else if (ownerText.startsWith("Player "))
            return Integer.parseInt(ownerText.substring(7)) + 1;
        else if (ownerText.equalsIgnoreCase("Opponent"))
            return 1;
        else
            throw new IllegalArgumentException("Unable to recognize player reference: " + ownerText);
    }

    /* package */ CardReference(String implicitReference, Matcher matcher) {
        //Matcher was matched back in ReferencePool.getCard
        this.refString = implicitReference;
        this.cardName = matcher.group("name");
        String modifier = matcher.group("modifier");
        if (modifier != null && !modifier.isEmpty()) {
            Matcher modifierMatcher = MODIFIER_PATTERN.matcher(modifier);
            if (!modifierMatcher.matches())
                throw new IllegalArgumentException("Syntax Error in card reference modifier: " + implicitReference);

            if (modifierMatcher.group("owner") != null)
                this.ownerIndex = ownerTextToIndex(modifierMatcher.group("owner"));

            if (modifierMatcher.group("quantity") != null) {
                this.quantity = Integer.parseInt(modifierMatcher.group("quantity"));
                if (this.quantity < 1)
                    throw new IllegalArgumentException("Card reference quantity cannot be less than 1: " + implicitReference);
            } else
                this.quantity = 1;

            if (modifierMatcher.group("index") != null) {
                //May not even need to track this; the different string alone would disambiguate.
                this.referenceIndex = Integer.parseInt(modifierMatcher.group("index"));
            } else
                this.referenceIndex = -1;
        } else {
            this.quantity = 1;
            this.referenceIndex = -1;
        }
    }

    /* package */ CardReference(String cardName, int ownerIndex, int quantity, ZoneType zone, String refString) { //For lands.
        this.cardName = cardName;
        this.ownerIndex = ownerIndex;
        this.quantity = quantity;
        this.zone = zone;
        this.referenceIndex = -99;
        this.refString = refString;
    }

    public String getGameStateString() {
        assert(this.id != -1); //ID wasn't assigned through reference pool. Tried to manually instantiate a reference?
        List<String> fields = new ArrayList<>();
        fields.add(card.getName());
        fields.add("Set:" + card.getEdition());
        fields.add("CN:" + card.getCollectorNumber());
        fields.add("Owner:P" + this.ownerIndex);
        String cardString = String.join("|", fields);

        List<String> joinedCards = new ArrayList<>(this.quantity);
        for(int i = 0; i < quantity; i++) {
            joinedCards.add(cardString + "|Id:" + (this.id + i));
        }

        return String.join(";", joinedCards);
    }

    /* package */ PaperCard getCard() {
        if(this.card == null)
            throw new IllegalStateException("Tried to fetch PaperCard before it has been supplied.");
        return this.card;
    }

    /* package */ void setCard(PaperCard card) {
        if(this.card != null)
            throw new IllegalStateException("PaperCard has already been supplied.");
        this.card = card;
    }

    /* package */ void setInferredZone(ZoneType zone) {
        if (this.zone == null)
            this.zone = zone;
    }

    /* package */ void setInferredOwner(int ownerIndex) {
        if (this.ownerIndex == -1)
            this.ownerIndex = ownerIndex;
    }

    @Override
        /* package */ boolean refersTo(IIdentifiable o) {
        return o.getId() >= this.id && o.getId() < this.id + this.quantity;
    }

    /**
     * Searches the game for the actual card objects that originated from this card reference.
     */
    public List<Card> findCards(Game game) {
        List<Card> out = new ArrayList<>(this.quantity);
        for(int i = this.id; i < this.id + this.quantity; i++) {
            Card card = game.findById(i);
            assert(card.getPaperCard().getName().equals(this.card.getName()));
            out.add(card);
        }
        return out;
    }

    @Override
    public int getQuantity() {
        return quantity;
    }

    /* package */ boolean isInferredBasics() {
        return this.referenceIndex == -99;
    }

    @Override
    public String toString() {
        return this.refString;
    }
}
