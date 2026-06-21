package media.barney.cognitive.core;

final class Thresholds {

    static final int DEFAULT = 8;

    private Thresholds() {
    }

    static int parse(String value) {
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Threshold must be a positive integer");
        }
        try {
            return validate(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Threshold must be a positive integer", ex);
        }
    }

    static int validate(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Threshold must be a positive integer");
        }
        return value;
    }
}
