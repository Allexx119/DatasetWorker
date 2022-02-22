package ru.asb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.ssh.SshWorker;
import ru.asb.security.Credential;
import ru.asb.util.Util;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Path systemPropertiesFile = Paths.get("resources/system.properties");
    private static final Properties systemProperties = new Properties();
    private static final FlowController flowController = new FlowController();
    private static final List<String> illegalArguments = new ArrayList<>();
    private static boolean saveUser = false;
    private static boolean openResultDir = false;

    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            Util.loadProperties(systemProperties, systemPropertiesFile);
            loadUserCredentialToSession(Paths.get(systemProperties.getProperty("file.source.crd")), flowController.getSourceSession());
            setDefaultParameters();
            parseArguments(args);
            Thread thread = new Thread(flowController, "flowControllerThread");
            thread.start();
            thread.join();

            if (!illegalArguments.isEmpty()) {
                log.info("\nНеверные параметры программы:");
                illegalArguments.forEach((str) -> log.info("{}", str));
            }

            if (saveUser && flowController.getSourceSession().isConnectionSuccessful()) {
                saveUserCredential(flowController.getSourceSession(), Paths.get(systemProperties.getProperty("file.source.crd")));
            }
            if (openResultDir) {
                openResultDir();
            }
        } catch (Exception ex) {
            log.error("Error: {}", Arrays.toString(ex.getStackTrace()));
        }
	}

    /**
     * Readme.txt
     *
     * Arguments priority note:
     * аргументы в конце списка имеют приоритет селнее,чем те, что стоят в начале списка аргументов.
     * Если аргумент params стоит в середине списка, аргументы из файлы вставляются на его место. То есть, все аргументы правее будут сильнее.
     *
     * Required parameters:
     * --descriptors="descriptor set" - descriptor folders set. If argument is not determined, use descriptor set from setting.properties
     *
     * Optional parameters:
     * --update-ds="host port" - SSH server info, where DS are situated. If argument is not determine, use SOURCE_SSH_HOST & SOURCE_SSH_PORT from setting.properties
     * --regexp="optional parameter" - filter datasets by regular expression in optional parameter, if no optional parameter, use regular expression from system.properties
     * --local - при выгрузке дата-сетов с локальной машины
     * --write-csv="optional parameter" - write datasets info to csv file with optional file name, if optional file name is not determined, use default file name.
     * --write-script="optional parameter" - write remove script file with optional file name, if optional file name is not determined, use default file name.
     * --validity-period="N days" - validity period.
     * --orphans - look for orphans.
     * --full - look for full datasets.
     * --ignore-exceptions - ignore exception list
     * --use-filter - work with datasets from file filter.txt
     * --save-user - save user info to secure credentials (program will use it during the next start).
     * --open-result - open result directory in windows after program execution finished.
     * --params="file_path" - add params from properties file (optional).
     * */

    private static void parseArguments(String[] args) {
        for (String arg : args) {
            Matcher matcher = Pattern.compile("--descriptors=(.*)", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                String rawString = matcher.group(1);
                String[] descriptors = rawString.split("\\s*[,;]\\s*");
                if (descriptors.length > 0)
                    flowController.setDescriptorDirs(descriptors);
                else
                    log.warn("Empty descriptors dirs array in parameters. Use values from system.properties");
                continue;
            }

            //Get ssh source host and port
            matcher = Pattern.compile("--update-ds(.*)", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                String serverInfoString = matcher.group(1);
                if (serverInfoString.length() > 0 && serverInfoString.startsWith("=")) {
                    try {
                        String[] serverInfo = serverInfoString.split(" ");
                        flowController.getSourceSession().setServerInfo(serverInfo[0], Integer.parseInt(serverInfo[1]));
                    } catch (IndexOutOfBoundsException ex) {
                        illegalArguments.add("ForceUpdateFlag: Не указан хост и порт, или указан в неверном формате.");
                    }
                }
                Path sourceCredential = Paths.get(systemProperties.getProperty("file.source.crd"));
                loadUserCredentialToSession(sourceCredential, flowController.getSourceSession());
                flowController.setDsFilesUpdateDelay(0);
                continue;
            }

            //Get regular expression
            matcher = Pattern.compile("--regexp(.*)", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                String regex = matcher.group(1);
                if (regex.length() > 0 && regex.startsWith("=")) {
                    flowController.setRegex(regex.substring(1));
                } else {
                    flowController.setRegex(systemProperties.getProperty("ds.filter.regex"));
                }
                continue;
            }

            //Update ds from local machine
            matcher = Pattern.compile("--local", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.changeFlowType(FlowType.LOCAL);
                flowController.getFlowType().setFromFile(false);
                continue;
            }

            //Execute script
            matcher = Pattern.compile("--execute", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.getFlowType().setExecute(true);
                continue;
            }

            //Execute script
            matcher = Pattern.compile("--execute-file", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.getFlowType().setExecute(true);
                flowController.getFlowType().setFromFile(true);
                continue;
            }

            //Write dataset info to csv file
            matcher = Pattern.compile("--write-csv", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.setWriteCsv(true);
                continue;
            }

            //Write script
            matcher = Pattern.compile("--write-script", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.setWriteScript(true);
                continue;
            }

            //Get validity period
            matcher = Pattern.compile("--validity-period=(.*)", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                String validityPeriodStr = matcher.group(1);
                flowController.setDsValidityPeriod(Integer.parseInt(validityPeriodStr));
                continue;
            }

            //Get use filter flag
            matcher = Pattern.compile("--use-filter", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.setFilterList(Arrays.asList(systemProperties.getProperty("ds.filter.list").split("\\s*[;,]\\s*")));
                continue;
            }

            //Get save user info flag
            matcher = Pattern.compile("--save-user", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                saveUser = true;
                continue;
            }

            //Get look for orphans flag
            matcher = Pattern.compile("--orphans", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.setLookForOrphans(true);
                continue;
            }

            //Get look for full ds flag
            matcher = Pattern.compile("--full", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.setLookForFull(true);
                continue;
            }

            //Get ignore exceptions flag
            matcher = Pattern.compile("--ignore-exceptions", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                flowController.setExceptions(null);
                continue;
            }

            //Get ignore exceptions flag
            matcher = Pattern.compile("--open-result", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                openResultDir = true;
                continue;
            }

            //Get properties file with parameters
            matcher = Pattern.compile("--params=(.*)", Pattern.CASE_INSENSITIVE).matcher(arg);
            if (matcher.find()) {
                Path paramPropertiesFile = Paths.get(matcher.group(1));
                if (Files.exists(paramPropertiesFile))
                    loadExecutionParameters(paramPropertiesFile);
                continue;
            }

            illegalArguments.add(arg);
        }
    }

    private static void loadExecutionParameters(Path paramPropertiesFile) {
        Properties parameters = new Properties();
        Util.loadProperties(parameters, paramPropertiesFile);
        List<String> arguments = new ArrayList<>();
        for (Map.Entry<Object, Object> argument : parameters.entrySet()) {
            Object valueObj = argument.getValue();
            if (valueObj != null && !valueObj.toString().isEmpty()) {
                String value = (String) valueObj;
                if (value.equalsIgnoreCase("TRUE"))
                    arguments.add((String)argument.getKey());
                else if (!value.equalsIgnoreCase("FALSE"))
                    arguments.add(String.format("%s=%s", argument.getKey(), value));
            } else {
                arguments.add((String)argument.getKey());
            }
        }
        parseArguments(arguments.toArray(new String[0]));
    }

    private static void setDefaultParameters() {
        if (systemProperties.isEmpty()) {
            Util.loadProperties(systemProperties, systemPropertiesFile);
        }
        flowController.getSourceSession().setServerInfo(systemProperties.getProperty("ssh.source.host"), Integer.parseInt(systemProperties.getProperty("ssh.source.port")));
        flowController.setDataDirs(systemProperties.getProperty("ds.data.list").split("\\s*[;,]\\s*"));

        flowController.setDsValidityPeriod(Integer.parseInt(systemProperties.getProperty("ds.filter.validityPeriod")));
        flowController.setDsLocalDir(systemProperties.getProperty("dir.source.ds"));

        flowController.setScriptPrefix(systemProperties.getProperty("script.prefix").trim());
        flowController.setScriptPostfix(systemProperties.getProperty("script.postfix").trim());

        flowController.setResultDir(Paths.get(systemProperties.getProperty("dir.result")));

        flowController.setExceptions(Arrays.asList(systemProperties.getProperty("ds.exception.list").split("\\s*[;,]\\s*")));

        flowController.setScriptExecutorThreadsCount(Integer.parseInt(systemProperties.getProperty("threads.count")));

        flowController.setDsFilesUpdateDelay(Integer.parseInt(systemProperties.getProperty("ds.filesUpdateDelay.hour"))*60*60);
    }

    public static void saveUserCredential(SshWorker session, Path file) {
        Credential user = new Credential(file);
        String[] credentials = session.getUserCredentials();
        user.putEncrypted("login", credentials[0]);
        user.putEncrypted("password", credentials[1]);
        user.save();
    }

    private static void loadUserCredentialToSession(Path credentialFile, SshWorker toSession) {
        if (Files.exists(credentialFile)) {
            Credential user = Credential.load(credentialFile);
            String login = user.get("login");
            String password = user.get("password");
            toSession.setAuth(login, password);
        }
    }

    public static Properties getSystemProperties() {
        return systemProperties;
    }

    public static void openResultDir() {
        File resultFolder = new File(systemProperties.getProperty("dir.result"));
        if (resultFolder.exists()) {
            try {
                Desktop.getDesktop().open(resultFolder);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            log.warn("Folder doesn't exists: {}%n", resultFolder);
        }
    }
}
