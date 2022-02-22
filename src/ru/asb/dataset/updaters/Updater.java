package ru.asb.dataset.updaters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public abstract class Updater {
    protected int dsFilesUpdateDelay;
    protected static Logger log = LogManager.getLogger(Updater.class);

    protected Updater(int dsFilesUpdateDelay) {
        this.dsFilesUpdateDelay = dsFilesUpdateDelay;
    }

    public abstract void update(String toDir, String... fromDirs)  throws InterruptedException, IOException;
}
