package net.caffeinemc.sodium.config.user.options;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

public class OptionGroup {
    private final ImmutableList<Option<?>> options;
    private final OptionGroupControl control;
    private OptionGroup(ImmutableList<Option<?>> options, OptionGroupControl control) {
        this.options = options;
        this.control = control;
    }

    public static Builder createBuilder() {
        return new Builder();
    }

    public ImmutableList<Option<?>> getOptions() {
        return this.options;
    }

    public OptionGroupControl getControl() {
        return control;
    }

    public static class Builder {
        private final List<Option<?>> options = new ArrayList<>();
        private OptionGroupControl control;

        public Builder add(Option<?> option) {
            this.options.add(option);

            return this;
        }

        public Builder addControl(OptionGroupControl control) {
            this.control = control;

            return this;
        }

        public OptionGroup build() {
            Validate.notEmpty(this.options, "At least one option must be specified");

            return new OptionGroup(ImmutableList.copyOf(this.options), control);
        }
    }
}
