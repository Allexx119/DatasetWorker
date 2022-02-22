package ru.asb.ssh;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.*;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.scp.client.ScpClient;
import org.apache.sshd.scp.client.ScpClientCreator;
import ru.asb.util.Util;

import java.io.*;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SshWorker {
    private static final Logger log = LogManager.getLogger(SshWorker.class);
    private static int attempts = 3;

    private boolean connectionSuccessful = false;
    private int port = -1;
    private String host = null;
    private String login = null;
    private String password = null;
    private SshClient sshClient;
    private ScpClient scpClient;
    private ClientSession session;
    private final long timeout;
    private final Path knownHostsPath;


    public SshWorker(long timeout) {
        this.timeout = TimeUnit.SECONDS.toMillis(timeout);
        this.knownHostsPath = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts");
    }

    /**
     * Open session with SSH server.
     * */
    public void openSession() throws IOException {
        try {
            if (!sessionIsOpen()) {
                if (host == null || port < 0 || login == null || password == null) {
                    askCredentials();
                }
                if (host != null && port > 0 && login != null && password != null) {
                    sshClient = SshClient.setUpDefaultClient();
                    sshClient.setServerKeyVerifier(initVerifier());
                    sshClient.start();
                    session = sshClient.connect(login, host, port).verify().getSession();
                    session.addPasswordIdentity(password);
                    session.auth().verify(timeout);
                    if (session.isOpen()) {
                        log.info("Session is opened");
                        connectionSuccessful = true;
                    } else {
                        log.warn("Unable to open session");
                        connectionSuccessful = false;
                        throw new IOException("Unable to open session", session.auth().getException().getCause());
                    }
                } else {
                    connectionSuccessful = false;
                    throw new SshException("Empty credentials (host, port, login or password)");
                }
            } else {
                log.info("Session is already opened");
            }
        } catch(SshException sshe) {
            connectionSuccessful = false;
            attempts--;
            log.error("Access denied");
            if (attempts > 0) {
                this.login = null;
                this.password = null;
                if (session.isOpen())
                    session.close();
                openSession();
            } else {
                throw sshe;
            }
        }
    }

    /**
     * Close SSH session with remote server.
     * */
    public void closeSession() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
                if (session.isClosed()) {
                    log.info("Session is closed");
                }
            }
            if (sshClient != null && sshClient.isOpen()) {
                sshClient.stop();
                if (sshClient.isClosed()) {
                    log.info("SSH client is closed");
                }
            }
        } catch (IOException ioe) {
            log.error("Error closing SSH session: " + ioe + " | " + Arrays.toString(ioe.getStackTrace()));
        }
    }

    public boolean sessionIsOpen() {
        if (session == null) return false;
        return session.isOpen();
    }

    /**
     * Execute command on remote SSH server;
     * @param command command to execute on the server;
     * @return InputStream with command result if command complete successfully, and empty InputStream if command failed.
     * */
    public InputStream execute(String command) throws IOException {
        if (sessionIsOpen()) {
            Thread delayedLogThread = Util.logDelayed("Executing:\t" + command, 2000);
            ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
            ChannelExec channelExec = session.createExecChannel(command);
            channelExec.setErr(errorOutputStream);
            channelExec.setOut(resultOutputStream);
            channelExec.open().verify(timeout);
            channelExec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
            int exitStatus = channelExec.getExitStatus();
            if (exitStatus == 0) {
                log.info("Complete:\t{} | Exit-status: {}", command, exitStatus);
            } else {
                log.warn("Warning:\t{} | Exit-status: {} | {}", command, exitStatus, errorOutputStream.toString().replaceAll("\n", " ").trim());
            }
            if (delayedLogThread.isAlive())
                delayedLogThread.interrupt();
            return new ByteArrayInputStream(resultOutputStream.toByteArray());
        } else {
            throw new SshException("SSH session is closed");
        }
    }

    /**
     * Execute command on remote SSH server and write the result to resultOutputStream;
     * @param command command to execute on the server;
     * */
    public void execute(String command, OutputStream resultOutputStream) throws IOException {
        if (sessionIsOpen()) {
            Thread delayedLogThread = Util.logDelayed("Executing:\t" + command, 2000);
            ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
            ChannelExec channelExec = session.createExecChannel(command);
            channelExec.setErr(errorOutputStream);
            channelExec.setOut(resultOutputStream);
            channelExec.open().verify(timeout);
            channelExec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
            int exitStatus = channelExec.getExitStatus();
            if (exitStatus == 0) {
                log.info("Complete:\t{} | Exit-status: {}", command, exitStatus);
            } else {
                log.warn("Warning:\t{} | Exit-status: {} | {}", command, exitStatus, errorOutputStream.toString().replaceAll("\n", " ").trim());
            }
            if (delayedLogThread.isAlive() && !delayedLogThread.isInterrupted())
                delayedLogThread.interrupt();
        } else {
            throw new SshException("SSH session is closed");
        }
    }

    /**
     * Open SCP client
     * */
    public void openScp() throws IOException {
        if (!sessionIsOpen())
            openSession();
        ScpClientCreator creator = ScpClientCreator.instance();
        this.scpClient = creator.createScpClient(session);
        log.info("SCP client is started");
    }

    /**
     * Send file from local machine to the remote server via scp client
     * */
    public void sendFile(Path localFile, String remoteDir) throws IOException {
        if (scpClient == null)
            openScp();
        scpClient.upload(localFile, remoteDir, ScpClient.Option.Recursive, ScpClient.Option.PreserveAttributes, ScpClient.Option.TargetIsDirectory);
        log.info("File {} send to the server dir {}", localFile, remoteDir);
    }

    /**
     * Download file from remote server to local machine
     * */
    public void downloadFile(String remoteFile, Path localDir) throws IOException {
        if (scpClient == null)
            openScp();
        scpClient.download(remoteFile, localDir, ScpClient.Option.Recursive, ScpClient.Option.PreserveAttributes, ScpClient.Option.TargetIsDirectory);
    }

    public void setAuth(String login, String password) {
        this.login = login;
        this.password = password;
    }

    public void setServerInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setParams(String host, int port, String login, String password) {
        if (sessionIsOpen()) closeSession();
        setServerInfo(host, port);
        setAuth(login, password);
    }

    public String[] getUserCredentials() {
        return new String[]{login, password};
    }

    public boolean isConnectionSuccessful() {
        return connectionSuccessful;
    }

    private KnownHostsServerKeyVerifier initVerifier() {
        final BlockingQueue<Boolean> hostKeyVerification = new ArrayBlockingQueue<>(1);
        KnownHostsServerKeyVerifier verifier = new DefaultKnownHostsServerKeyVerifier(new ServerKeyVerifier() {
            @Override
            public boolean verifyServerKey(ClientSession cs, SocketAddress sa, PublicKey pk) {
                boolean ret = true;
                hostKeyVerification.offer(ret);
                return ret; // ask user to verify unknown public key
            }
        }, true, knownHostsPath) {
            @Override
            protected boolean acceptKnownHostEntry(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey, KnownHostEntry entry) {
                boolean ret = super.acceptKnownHostEntry(clientSession, remoteAddress, serverKey, entry);
                hostKeyVerification.offer(ret);
                return ret;
            }
        };

        verifier.setModifiedServerKeyAcceptor(new ModifiedServerKeyAcceptor() {
            @Override
            public boolean acceptModifiedServerKey(ClientSession cs, SocketAddress sa, KnownHostEntry khe, PublicKey expected, PublicKey actual) throws Exception {
                boolean ret = true;
                hostKeyVerification.offer(ret);
                return ret; // ask user to verify public key change
            }
        });
        return verifier;
    }

    private void askCredentials() throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new InputStream() {

            @Override
            public int read() throws IOException {
                return System.in.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return System.in.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return System.in.read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                return System.in.skip(n);
            }

            @Override
            public void close() throws IOException {
                super.close();
            }

            @Override
            public int available() throws IOException {
                return System.in.available();
            }

            @Override
            public synchronized void mark(int readlimit) {
                System.in.mark(readlimit);
            }

            @Override
            public synchronized void reset() throws IOException {
                System.in.reset();
            }

            @Override
            public boolean markSupported() {
                return System.in.markSupported();
            }
        }))) {
            String host;
            int port;
            if (this.host == null || this.port < 0) {
                log.info("host: ");
                if (System.console() != null) {
                    host = System.console().readLine();
                    log.info("port: ");
                    port = Integer.parseInt(System.console().readLine());
                } else {
                    host = bufferedReader.readLine();
                    log.info("port: ");
                    port = Integer.parseInt(bufferedReader.readLine());
                }
                setServerInfo(host, port);
            }

            String login;
            String password;

            log.info("Connect to {} : {}", this.host, this.port);
            log.info("login: ");
            if (System.console() != null) {
                login = System.console().readLine();
                log.info("password: ");
                password = new String(System.console().readPassword());
            } else {
                login = bufferedReader.readLine();
                log.info("password: ");
                password = bufferedReader.readLine();
            }
            setAuth(login, password);
        }
    }
}
