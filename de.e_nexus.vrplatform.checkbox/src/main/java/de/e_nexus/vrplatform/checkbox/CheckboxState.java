package de.e_nexus.vrplatform.checkbox;

/** The three states a {@link Checkbox} can show. */
public enum CheckboxState {
    UNCHECKED("0"),
    CHECKED("x"),
    INDETERMINATE("-");

    private final String symbol;

    CheckboxState(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** The state {@link Checkbox#toggle()} moves to from this one. */
    public CheckboxState next() {
        return switch (this) {
            case UNCHECKED -> CHECKED;
            case CHECKED -> INDETERMINATE;
            case INDETERMINATE -> UNCHECKED;
        };
    }
}
