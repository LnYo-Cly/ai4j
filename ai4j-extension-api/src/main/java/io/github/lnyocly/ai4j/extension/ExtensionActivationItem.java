package io.github.lnyocly.ai4j.extension;

public final class ExtensionActivationItem {

    private final String type;
    private final String name;
    private final String state;
    private final String reason;

    public ExtensionActivationItem(String type, String name, String state, String reason) {
        this.type = ExtensionManifest.requireId(type, "activation item type");
        this.name = ExtensionManifest.requireId(name, "activation item name");
        this.state = ExtensionManifest.requireId(state, "activation item state");
        this.reason = ExtensionManifest.emptyToNull(reason);
    }

    public static ExtensionActivationItem active(String type, String name, String reason) {
        return new ExtensionActivationItem(type, name, "active", reason);
    }

    public static ExtensionActivationItem inactive(String type, String name, String reason) {
        return new ExtensionActivationItem(type, name, "inactive", reason);
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getReason() {
        return reason;
    }

    public boolean isActive() {
        return "active".equals(state);
    }
}
