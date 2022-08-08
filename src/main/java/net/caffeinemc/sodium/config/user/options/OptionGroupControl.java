package net.caffeinemc.sodium.config.user.options;

public class OptionGroupControl {
    private boolean shouldDisplay;

    public void setDisplay(boolean shouldDisplay) {
        this.shouldDisplay = shouldDisplay;
    }

    public boolean shouldDisplay() {
        return shouldDisplay;
    }
}
