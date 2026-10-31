package dev.bento.menu.api.v1;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable click feedback: sound plus optional animated title frames.
 */
public final class Feedback {

    private final String sound;
    private final float volume;
    private final float pitch;
    private final List<String> titleFrames;
    private final String subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;
    private final Duration frameInterval;

    private Feedback(Builder builder) {
        this.sound = builder.sound;
        this.volume = builder.volume;
        this.pitch = builder.pitch;
        this.titleFrames = List.copyOf(builder.titleFrames);
        this.subtitle = builder.subtitle;
        this.fadeIn = builder.fadeIn;
        this.stay = builder.stay;
        this.fadeOut = builder.fadeOut;
        this.frameInterval = builder.frameInterval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String sound() {
        return sound;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    public List<String> titleFrames() {
        return titleFrames;
    }

    public String subtitle() {
        return subtitle;
    }

    public int fadeIn() {
        return fadeIn;
    }

    public int stay() {
        return stay;
    }

    public int fadeOut() {
        return fadeOut;
    }

    public Duration frameInterval() {
        return frameInterval;
    }

    public static final class Builder {
        private final List<String> titleFrames = new ArrayList<>();
        private String sound;
        private float volume = 1.0f;
        private float pitch = 1.0f;
        private String subtitle;
        private int fadeIn = 5;
        private int stay = 18;
        private int fadeOut = 5;
        private Duration frameInterval = Duration.ofMillis(120);

        public Builder sound(String sound) {
            this.sound = sound;
            return this;
        }

        public Builder sound(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            return this;
        }

        public Builder title(String title) {
            if (title != null && !title.isEmpty()) {
                this.titleFrames.add(title);
            }
            return this;
        }

        public Builder titleFrames(List<String> frames) {
            this.titleFrames.clear();
            if (frames != null) {
                this.titleFrames.addAll(frames);
            }
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder times(int fadeIn, int stay, int fadeOut) {
            this.fadeIn = Math.max(0, fadeIn);
            this.stay = Math.max(1, stay);
            this.fadeOut = Math.max(0, fadeOut);
            return this;
        }

        public Builder frameInterval(Duration interval) {
            this.frameInterval = interval == null ? Duration.ofMillis(120) : interval;
            return this;
        }

        public Feedback build() {
            return new Feedback(this);
        }
    }
}
