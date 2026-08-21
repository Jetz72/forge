package forge.game.logic;

import forge.game.ability.AbilityKey;
import forge.game.event.GameEvent;
import forge.game.event.IGameEventVisitor;
import forge.game.spellability.SpellAbility;

import java.util.Map;

/* package */ abstract class TestGameEvent implements GameEvent {
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return null; //These aren't handled by other event handlers.
    }

    static class Trigger extends TestGameEvent {
        final SpellAbility spellAbility;
        final forge.game.trigger.Trigger trigger;
        final Map<AbilityKey, Object> triggeringObjects;
        final int triggerIndex;

        Trigger(SpellAbility triggerSA) {
            this.spellAbility = triggerSA;
            this.trigger = triggerSA.getTrigger();
            this.triggeringObjects = trigger.getOverridingAbility().getTriggeringObjects();
            this.triggerIndex = trigger.getHostCard().getTriggers().indexOf(trigger);
        }
    }
}
