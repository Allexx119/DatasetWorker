package ru.asb.dataset.executors;

import org.apache.sshd.common.SshException;
import ru.asb.script.Script;
import ru.asb.ssh.SshWorker;

import java.io.IOException;
import java.util.*;

public class ScriptExecutor extends RemoteExecutor {
    private final List<String> commands;

    public ScriptExecutor(SshWorker executeSession, Script script) {
        super(executeSession);
        this.commands = script.getCommands();

    }

    public synchronized void run() {
        completeCommandsCount.set(0);
        log.info("Run script executor in {} threads", threadsNum);
        log.info("Total commands count: {}", commands.size());
        try {
            openSession();
            execute(commands);
            executeFailed();
            log.info("Script execution finished.  Commands completed: {}", super.completeCommandsCount);
        } catch (SshException sshe) {
            log.warn("ExecutionSession is closed. {} | {}", sshe.getMessage(), Arrays.toString(sshe.getStackTrace()));
        } catch (IOException ioe) {
            log.warn("{} | {}",ioe.getMessage(), Arrays.toString(ioe.getStackTrace()));
        } finally {
            closeSession();
        }
    }


}
