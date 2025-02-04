package net.caffeinemc.sodium.gui.config;

import net.caffeinemc.sodium.config.user.options.Option;
import net.caffeinemc.sodium.config.user.options.TextProvider;
import net.caffeinemc.sodium.interop.vanilla.math.vector.Dim2i;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.TranslatableOption;
import org.apache.commons.lang3.Validate;

public class CyclingControl<T extends Enum<T>> implements Control<T> {
    private final Option<T> option;
    private final T[] allowedValues;
    private final Text[] names;
    private final int maxWidth;

    public CyclingControl(Option<T> option, Class<T> enumType) {
        this(option, enumType, enumType.getEnumConstants());
    }

    public CyclingControl(Option<T> option, Class<T> enumType, Text[] names) {
        T[] universe = enumType.getEnumConstants();

        Validate.isTrue(universe.length == names.length, "Mismatch between universe length and names array length");
        Validate.notEmpty(universe, "The enum universe must contain at least one item");

        this.option = option;
        this.allowedValues = universe;
        this.names = names;
        maxWidth = 70;
    }

    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        T[] universe = enumType.getEnumConstants();

        this.option = option;
        this.allowedValues = allowedValues;
        this.names = new Text[universe.length];
        int width = 0;
        for (int i = 0; i < this.names.length; i++) {
            Text name;
            T value = universe[i];

            if (value instanceof TextProvider textProvider) {
                name = textProvider.getLocalizedName();
            } else if (value instanceof TranslatableOption translatableOption) {
                name = translatableOption.getText();
            } else {
                name = Text.literal(value.name());
            }
            width = Math.max(width,  MinecraftClient.getInstance().textRenderer.getWidth(name));

            this.names[i] = name;
        }
        maxWidth = width;
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public ControlElement<T> createElement(Dim2i dim) {
        return new CyclingControlElement<>(this.option, dim, this.allowedValues, this.names);
    }

    @Override
    public int getMaxWidth() {
        return maxWidth;
    }

    private static class CyclingControlElement<T extends Enum<T>> extends ControlElement<T> {
        private final T[] allowedValues;
        private final Text[] names;
        private int currentIndex;

        public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, Text[] names) {
            super(option, dim);

            this.allowedValues = allowedValues;
            this.names = names;
            this.currentIndex = 0;

            // select first allowed option
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == option.getValue()) {
                    this.currentIndex = i;
                    break;
                }
            }
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
            super.render(matrixStack, mouseX, mouseY, delta);

            Enum<T> value = this.option.getValue();
            Text name = this.names[value.ordinal()];

            int strWidth = this.getStringWidth(name);
            this.drawString(matrixStack, name, this.dim.getLimitX() - strWidth - 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                this.currentIndex = Math.floorMod(this.option.getValue().ordinal() + (Screen.hasShiftDown() ? -1 : 1), this.allowedValues.length);
                this.option.setValue(this.allowedValues[this.currentIndex]);
                this.playClickSound();

                return true;
            }

            return false;
        }
    }
}
