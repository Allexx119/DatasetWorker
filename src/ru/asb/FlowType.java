package ru.asb;

public enum FlowType {
    LOCAL,
    REMOTE;

    private boolean execute = false;
    private boolean fromFile = false;

    public void setExecute(boolean execute) {
        this.execute = execute;
    }

    public void setFromFile(boolean fromFile) {
        this.fromFile = fromFile;
    }

    public boolean isExecute() {
        return execute;
    }

    public boolean isFromFile() {
        return fromFile;
    }
}
