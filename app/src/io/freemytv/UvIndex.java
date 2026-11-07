package io.freemytv;

/** WHO UV-index ranges, colors and short protection advice. */
final class UvIndex {
    private static final int LOW = 0, MODERATE = 1, HIGH = 2, VERY_HIGH = 3, EXTREME = 4;

    private UvIndex() {}

    static boolean available(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }

    static int value(double uv) {
        return Math.max(0, (int) Math.round(uv));
    }

    private static int category(int value) {
        if (value <= 2) return LOW;
        if (value <= 5) return MODERATE;
        if (value <= 7) return HIGH;
        if (value <= 10) return VERY_HIGH;
        return EXTREME;
    }

    static String level(int value) {
        switch (category(value)) {
            case LOW: return Ui.tr("Low");
            case MODERATE: return Ui.tr("Moderate");
            case HIGH: return Ui.tr("High");
            case VERY_HIGH: return Ui.tr("Very high");
            default: return Ui.tr("Extreme");
        }
    }

    static String advice(int value) {
        switch (category(value)) {
            case LOW: return Ui.tr("Enjoy being outdoors.");
            case MODERATE:
            case HIGH: return Ui.tr("Seek shade at midday. Wear a shirt, hat and sunscreen.");
            default: return Ui.tr("Avoid midday sun. Seek shade and use full sun protection.");
        }
    }

    static int color(int value) {
        switch (category(value)) {
            case LOW: return 0xFF289500;
            case MODERATE: return 0xFFF7E400;
            case HIGH: return 0xFFF85900;
            case VERY_HIGH: return 0xFFD8001D;
            default: return 0xFF6B49C8;
        }
    }

    static int textColor(int value) {
        int category = category(value);
        return category == MODERATE || category == HIGH ? 0xFF171717 : 0xFFFFFFFF;
    }
}
