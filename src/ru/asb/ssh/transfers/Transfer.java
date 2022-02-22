package ru.asb.ssh.transfers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.ssh.SshWorker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class Transfer {
    private final SshWorker targetSession;
    private final Logger log;

    public Transfer(SshWorker targetSession) {
        this.targetSession = targetSession;
        this.log = LogManager.getLogger(Transfer.class);
    }

    public boolean send(Path localFile, String remoteDirectory) {
        try {
            if (localFile != null) {
                targetSession.openSession();
                targetSession.openScp();
                targetSession.sendFile(localFile, remoteDirectory);
                targetSession.closeSession();
                return true;
            } else {
                log.warn("File is not determine. Nothing to send");
                return false;
            }
        } catch (IOException ioe) {
            log.error("Error sending file: {} | {}", ioe, Arrays.toString(ioe.getStackTrace()));
            return false;
        }
    }
}

//Get ssh target host and port
//            matcher = Pattern.compile("--send-script(.*)", Pattern.CASE_INSENSITIVE).matcher(arg);
//            if (matcher.find()) {
//                String serverInfoString = matcher.group(1);
//                if (serverInfoString.length() > 0 && serverInfoString.startsWith("=")) {
//                    try {
//                        String[] serverInfo = serverInfoString.split(" ");
//                        dsExecutor.getTargetSession().setServerInfo(serverInfo[0], Integer.parseInt(serverInfo[1]));
//                    } catch (IndexOutOfBoundsException ex) {
//                        illegalArguments.add("SendScriptFlag: Не указан хост и порт, или указан в неверном формате.");
//                    }
//                } else {
//                    dsExecutor.getTargetSession().setServerInfo(systemProperties.getProperty("TARGET_HOST"), Integer.parseInt(systemProperties.getProperty("TARGET_PORT")));
//                    dsExecutor.setTargetDir(systemProperties.getProperty("TARGET_DIR"));
//                }
//                loadUserCredentialToSession(Paths.get(systemProperties.getProperty("TARGET_CRD")), dsExecutor.getSourceSession());
//                dsExecutor.setSendScriptFlag(true);
//                continue;
//            }