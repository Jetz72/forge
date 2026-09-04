package forge.game.logic;

import forge.game.Game;
import forge.game.IIdentifiable;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class StackReference extends LiveReference<SpellAbility> {

    StackReference(String label) {
        super(label);
    }

    protected Set<SpellAbility> resolvedSAs = null;

    @Override
    void setResolved(Collection<? extends IIdentifiable> resolved) {
        super.setResolved(resolved);
        assert(resolved.stream().allMatch(SpellAbility.class::isInstance));
        this.resolvedSAs = resolved.stream().map(SpellAbility.class::cast).collect(Collectors.toSet());
    }

    @Override
    public Set<SpellAbility> getResolved(Game game) {
        return resolvedSAs;
    }

    @Override
    public boolean refersTo(IIdentifiable o) {
        if(o instanceof SpellAbilityStackInstance stackInstance)
            return this.resolvedSAs.contains(stackInstance.getSpellAbility());
        return o instanceof SpellAbility && this.resolved.contains(o.getId());
    }
}
