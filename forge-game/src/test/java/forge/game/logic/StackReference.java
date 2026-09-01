package forge.game.logic;

import forge.game.IIdentifiable;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;

public class StackReference extends LiveReference<SpellAbility> {

    StackReference(String label) {
        super(label);
    }

    @Override
    public boolean refersTo(IIdentifiable o) {
        if(o instanceof SpellAbilityStackInstance stackInstance)
            return this.resolved.contains(stackInstance.getSpellAbility());
        return o instanceof SpellAbility && this.resolved.contains(o);
    }
}
