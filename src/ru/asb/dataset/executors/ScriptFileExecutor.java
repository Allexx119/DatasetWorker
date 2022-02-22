package ru.asb.dataset.executors;

import org.apache.sshd.common.SshException;
import ru.asb.ssh.SshWorker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ScriptFileExecutor extends RemoteExecutor {
    private final Set<Path> scriptPaths;
    private int bufferSize = 1000;

    public ScriptFileExecutor(SshWorker executeSession, Path scriptPath) {
        super(executeSession);
        this.scriptPaths = new HashSet<>();
        scriptPaths.add(scriptPath);
    }

    public ScriptFileExecutor(SshWorker executeSession, Set<Path> scriptPaths) {
        super(executeSession);
        this.scriptPaths = scriptPaths;
    }

    public synchronized void run() {
        completeCommandsCount.set(0);
        log.info("Run script executor in {} threads", threadsNum);
        try {
            openSession();
            for (Path file : scriptPaths) {
                if (Files.exists(file)) {
                    BufferedReader reader = new BufferedReader(new FileReader(file.toFile()));
                    List<String> commandsBuffer;
                    while(!(commandsBuffer = readCommands(reader, bufferSize)).isEmpty()) {
                        execute(commandsBuffer);
                    }
                    reader.close();
                } else {
                    log.warn("No file: {}", file);
                }
            }
            executeFailed();
            log.info("Script execution finished. Commands completed: {}", super.completeCommandsCount);
        } catch (SshException sshe) {
            log.warn("ExecutionSession is closed. {} | {}", sshe.getMessage(), Arrays.toString(sshe.getStackTrace()));
        } catch (IOException ioe) {
            log.warn("{} | {}",ioe.getMessage(), Arrays.toString(ioe.getStackTrace()));
        } finally {
            closeSession();
        }
    }

    /**
     * Читает из ридера количество комманд, которое равно размеру буфера
     * */
    private List<String> readCommands(BufferedReader reader, int bufferSize) throws IOException {
        List<String> commands = new ArrayList<>();
        String line;
        int commandsCounter = 0;
        StringBuilder commandBuilder = new StringBuilder();
        while((line = reader.readLine()) != null && commandsCounter <= bufferSize) {
            line = line.trim();
            if (!line.equals("#--")) {
                if (!line.startsWith("#"))
                    commandBuilder.append(line).append(" ");
            } else {
                String command = commandBuilder.toString().trim();
                if (!command.isEmpty()) {
                    commands.add(command);
                    commandsCounter++;
                }
                commandBuilder = new StringBuilder();
            }
        }
        return commands;
    }

    /**
     * Установить размер буфера комманд
     * */
    public synchronized void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
