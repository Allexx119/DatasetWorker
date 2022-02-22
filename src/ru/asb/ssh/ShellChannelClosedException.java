package ru.asb.ssh;

import java.io.IOException;

public class ShellChannelClosedException extends IOException {
    public ShellChannelClosedException() {
    }

    public ShellChannelClosedException(String message) {
        super(message);
    }
}
