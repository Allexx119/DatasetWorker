package ru.asb.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.util.Util;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Script {
    private final StringBuilder scriptBuilder = new StringBuilder();
    private final Collection<Scriptable> collection = new ArrayList<>();
    private boolean inline = false;
    private String prefix = null;
    private String postfix = null;
    private final static Logger log = LogManager.getLogger(Script.class);

    public Script(Scriptable scriptable) {
        collection.add(scriptable);
    }

    public Script(Collection<? extends Scriptable> collection) {
        this.collection.addAll(collection);
    }

    public Script(Collection<? extends Scriptable> collection, String prefix, String postfix) {
        this.collection.addAll(collection);
        this.prefix = prefix;
        this.postfix = postfix;
    }

    public Script inline() {
        return inline(true);
    }

    public Script inline(boolean inline) {
        this.inline = inline;
        return this;
    }

    public List<String> getCommands() {
        List<String> commands = new ArrayList<>();
        for (Scriptable scriptable : collection) {
            StringBuilder commandsBuilder = new StringBuilder();
            for (String scriptRow : scriptable.getScriptRows(prefix, postfix)) {
                commandsBuilder.append(scriptRow).append(inline?" ":"\n");
            }
            commands.add(commandsBuilder.toString());
        }
        return commands;
    }

    private void generateBash(){
        if (!inline) {
            scriptBuilder.insert(0, "#!/bin/bash\n");
        }
        for (Scriptable scriptable : collection) {
            if (!inline && collection.size() > 1) {
                scriptBuilder.append("#--\n");
            }
            for (String scriptRow : scriptable.getScriptRows(prefix, postfix)) {
                scriptBuilder.append(scriptRow).append(inline?" ":"\n");
            }
        }
    }

    public Path writeBash(Path scriptFile) {
        if(!scriptFile.getFileName().toString().toLowerCase().endsWith(".sh")) {
            scriptFile.resolve(".sh");
        }
        try {
            log.info("Write script {}", scriptFile);
            generateBash();
            scriptBuilder.append("echo \"Script finished.\"\n");
            if (Files.notExists(scriptFile.getParent())) Files.createDirectories(scriptFile.getParent());
            Files.write(scriptFile, scriptBuilder.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        }  catch (FileSystemException e) {
            scriptFile = Util.getAnotherName(scriptFile);
            writeBash(scriptFile);
        } catch (IOException ioe) {
            log.error("Error writing script {} | {}", scriptFile, ioe);
        }
        return scriptFile;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setPostfix(String postfix) {
        this.postfix = postfix;
    }

    public String toString() {
        generateBash();
        return scriptBuilder.toString();
    }
}
