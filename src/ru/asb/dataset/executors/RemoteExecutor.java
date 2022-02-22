package ru.asb.dataset.executors;

import org.apache.sshd.common.SshException;
import ru.asb.ssh.SshWorker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class RemoteExecutor extends Executor {
    protected final SshWorker executionSession;
    protected final List<String> failedCommands;

    protected RemoteExecutor(SshWorker executionSession) {
        super();
        this.executionSession = executionSession;
        this.failedCommands = new ArrayList<>();
    }

    /**
     * Открывает сессию
     * */
    protected void openSession() throws IOException {
        this.executionSession.openSession();
    }

    /**
     * Закрывает сессию
     * */
    protected void closeSession() {
        this.executionSession.closeSession();
    }

    /**
     * Выполнить команды
     * */
    protected void execute(Collection<String> commands) throws SshException {
        ExecutorService commandsExecutor = initExecutor();
        if (!executionSession.sessionIsOpen())
            throw new SshException("ExecutionSession is closed");
        try {
            for (String command : commands) {
                commandsExecutor.submit(() -> {
                    try {
                        executionSession.execute(command);
                        completeCommandsCount.getAndIncrement();
                    } catch (SshException sshe) {
                        failedCommands.add(command);
                        log.warn("Command failed: {} | {} | {}", command, sshe, Arrays.toString(sshe.getStackTrace()));
                    } catch (Exception e) {
                        log.error("Error executing command: {} | {} | {}", command, e, Arrays.toString(e.getStackTrace()));
                        commandsExecutor.shutdownNow();
                    }
                });
            }

            commandsExecutor.shutdown();
            while (!commandsExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                log.warn("Executors await termination timeout is elapsed");
            }
        } catch (InterruptedException ie) {
            commandsExecutor.shutdownNow();
            log.info("Script execution interrupted manually");
        } catch (Exception e) {
            log.error("Dataset decommission error: {} | {}", e, Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * Повторить неуспешные команды
     * */
    protected void executeFailed() {
        if (!failedCommands.isEmpty()) {
            log.info("Try to execute failed commands");
            for (String failedCommand : failedCommands) {
                try {
                    executionSession.execute(failedCommand);
                } catch (Exception e) {
                    log.error("Error executing command: {} | {} | {}", failedCommand, e, Arrays.toString(e.getStackTrace()));
                }
            }
        }
    }

}
