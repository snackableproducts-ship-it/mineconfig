package dev.xmine.config.core;

public enum Category {
    COMBAT("Combat"), MOVEMENT("Movement"), PLAYER("Player"), RENDER("Render"),
    WORLD("World"), HUD("HUD"), MISC("Misc");

    private final String label;
    Category(String label) { this.label = label; }
    public String label() { return label; }
}
