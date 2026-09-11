package forge.game.logic;

import forge.game.Game;
import forge.game.IIdentifiable;
import forge.util.IHasName;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract class LiveReference <T extends IIdentifiable> implements ITestReference<T> {
    static final Pattern LIVE_REFERENCE_PATTERN = Pattern.compile("^\\s*<(?<label>[^<>]+)>\\s*$");
    public final String label;
    //protected int id = -1;

    protected Set<Integer> resolved;
    private String requireSingularError = null;
    private Map<String, Integer> namesAndAmounts;

    LiveReference(String label) {
        this.label = label;
    }

    /* package */ void setResolved(Collection<? extends IIdentifiable> resolved) {
        assert(this.resolved == null);
        if(this.requireSingularError != null && resolved.size() > 1)
            throw new UnsupportedOperationException(this.requireSingularError);
        this.resolved = resolved.stream().map(IIdentifiable::getId).collect(Collectors.toSet());
        this.namesAndAmounts = resolved.stream().collect(Collectors.toMap(
                i -> (i instanceof IHasName n) ? n.getName() : i.toString(),
                i -> 1,
                Integer::sum)
        );
    }

    @Override
    public abstract Set<T> getResolved(Game game);

    /* package */ boolean isResolved() {
        return resolved != null;
    }

    @Override
    public int getQuantity() {
        if(this.resolved == null) {
            assert(false); //Could allow this but shouldn't be needed. Would rather have the notice that something was overlooked.
            return -1;
        }
        return this.resolved.size();
    }

    /* package */ boolean matchesResolved(Collection<? extends IIdentifiable> input) {
        return input.stream().map(IIdentifiable::getId).collect(Collectors.toSet()).equals(this.resolved);
    }

    @Override
    public void assertSingular(String error) {
        if(this.resolved != null) {
            ITestReference.super.assertSingular(error);
        }
        this.requireSingularError = error; //Context needs a singular reference. Blow up if we later get given multiple resolved objects.
    }

    @Override
    public String toString() {
        if(this.namesAndAmounts != null)
            return String.format("'%s' %s", this.label, GameLogicTestUtils.formatNamesAndAmounts(this.namesAndAmounts));
        return String.format("'%s'", this.label);
    }
}
