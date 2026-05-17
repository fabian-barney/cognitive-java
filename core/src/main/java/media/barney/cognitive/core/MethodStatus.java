package media.barney.cognitive.core;

enum MethodStatus {
    PASSED("passed"),
    FAILED("failed");

    private final String value;

    MethodStatus(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
