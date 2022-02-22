package ru.asb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.dataset.Dataset;
import ru.asb.dataset.DatasetWorker;
import ru.asb.dataset.Group;
import ru.asb.dataset.collectors.DatasetMapCollector;
import ru.asb.dataset.executors.LocalRemover;
import ru.asb.dataset.updaters.LocalUpdater;
import ru.asb.dataset.updaters.ServerUpdater;
import ru.asb.dataset.executors.Executor;
import ru.asb.dataset.executors.ScriptExecutor;
import ru.asb.dataset.executors.ScriptFileExecutor;
import ru.asb.ssh.SshWorker;
import ru.asb.script.Script;
import ru.asb.util.Unit;
import ru.asb.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class FlowController implements Runnable {
    private static final Logger log = LogManager.getLogger(FlowController.class);
    private final SshWorker sourceSession = new SshWorker(10L);
    private FlowType flowType = FlowType.REMOTE;

    private boolean lookForOrphans = false;
    private boolean lookForFull = false;
    private boolean writeCsv = false;
    private boolean writeScript = false;
    private int dsValidityPeriod = 0;
    private int dsFilesUpdateDelay = 0;
    private int scriptExecutorThreadsCount = 1;

    private List<String> exceptions = null;
    private List<String> filterList = null;

    private String regex = null;
    private String scriptPrefix = null;
    private String scriptPostfix = null;
    private Path resultDir = null;

    //REQUIRED PARAMETERS
    private Set<String> descriptorDirs = null;
    private Set<String> dataDirs = null;
    private String dsLocalDir = null;

    @Override
    public void run() {
        this.logRunParameters();
        this.removeDatasets();
    }

    public void removeDatasets() {
        List<Dataset> dsResultList;
        List<Group> dsGroupResultList;
        try {
            DatasetWorker dsWorker;
            switch (flowType) {
                case LOCAL: dsWorker = new DatasetWorker(dsLocalDir, new DatasetMapCollector(), new LocalUpdater(dsFilesUpdateDelay)); break;
                case REMOTE: dsWorker = new DatasetWorker(dsLocalDir, new DatasetMapCollector(), new ServerUpdater(sourceSession, dsFilesUpdateDelay)); break;
                default: throw new IllegalArgumentException();
            }

            //Обновление дата-сетов
            Collection<Path> dsDescriptorFiles = dsWorker.getUpdatedDatasetsFiles(descriptorDirs.toArray(new String[0]));
            Collection<Path> dsDataFiles = dsWorker.getUpdatedDatasetsFiles(dataDirs.toArray(new String[0]));

            //Сбор дата-сетов
            dsWorker.collectDatasets(dsDescriptorFiles,true);
            dsWorker.collectDatasets(dsDataFiles, false);
            log.info("Common datasets count: {} | {} Gb", dsWorker.getDatasets().size(), String.format("%6.3f", Util.getDsListSize(dsWorker.getDatasets(), Unit.GIGABYTE)));

            //Фильтрация дата-сетов
            dsResultList = dsWorker.filter().notIn(exceptions).onlyIn(filterList).orphans(lookForOrphans).full(lookForFull).matched(regex).expired(dsValidityPeriod).getList();


            Path scriptFile = null;
            if (flowType == FlowType.REMOTE && flowType.isFromFile())
                writeScript = true;

            if (writeScript) {
                Path scriptDir = resultDir.resolve("scripts");
                if (Files.notExists(scriptDir)) {
                    Files.createDirectories(scriptDir);
                }
                scriptFile = new Script(dsResultList, scriptPrefix, scriptPostfix).writeBash(scriptDir.resolve(String.format("rm_%s.sh", Util.getScriptName(dsWorker.getUpdatedDatasetsFiles(descriptorDirs.toArray(new String[0]))).toLowerCase())));
            }

            if (writeCsv) {
                if (dsResultList.size() > 0)
                    Util.writeCSV(resultDir.resolve("datasets.csv"), dsResultList.get(0).getCsvHeader(), dsResultList);
                else
                    log.info("DS list is empty.");
                dsGroupResultList = dsWorker.collectGroups(dsResultList);
                if (dsGroupResultList.size() > 0)
                    Util.writeCSV(resultDir.resolve("groups.csv"), dsGroupResultList.get(0).getCsvHeader(), dsGroupResultList);
                else
                    log.info("Groups list is empty.");
            }

            switch (flowType) {
                case LOCAL:
                    Executor executor = new LocalRemover(dsResultList);
                    executor.run(); //В текущей реализации нет необходимости запускать в отдельном потоке
                    break;
                case REMOTE:
                    if (flowType.isFromFile()) {
                        executor = new ScriptFileExecutor(sourceSession, scriptFile);
                    } else {
                        Script script = new Script(dsResultList, scriptPrefix, scriptPostfix).inline();
                        executor = new ScriptExecutor(sourceSession, script);
                    }
                    executor.setThreadsNum(scriptExecutorThreadsCount);
                    executor.run(); //В текущей реализации нет необходимости запускать в отдельном потоке
                    break;
                default: break;
            }


            log.info("Processed datasets size: {}", String.format("%-8.3f Gb", Util.size(size(dsResultList), Unit.GIGABYTE)));

            log.info("Datasets executor finished");
        } catch (InterruptedException ie) {
            log.info("Dataset executor process interrupted");
        } catch (IOException  ioe) {
            log.error("Error: {} {}",ioe.getMessage(), Arrays.toString(ioe.getStackTrace()));
            ioe.printStackTrace();
        } finally {
            if (sourceSession.sessionIsOpen()) sourceSession.closeSession();
        }
    }

    private String[] presetDirs(String[] dirs) {
        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i] != null) {
                if (!dirs[i].isEmpty()) {
                    dirs[i] = dirs[i].replaceAll("\\\\", "/");
                    if (!dirs[i].startsWith("/")) dirs[i] = "/" + dirs[i];
                    if (!dirs[i].endsWith("/")) dirs[i] = dirs[i] + "/";
                } else {
                    dirs[i] = null;
                }
            }
        }
        return dirs;
    }

    /**
     * Возвращает размер всего списка.
     * */
    private long size(List<Dataset> datasetList) {
        long _size = 0L;
        for (Dataset dataset : datasetList) {
            _size += dataset.size();
        }
        return _size;
    }

    public void setDescriptorDirs(String[] descriptorDirs) {
        if (descriptorDirs.length > 0) {
            this.descriptorDirs = new HashSet<>(Arrays.asList(presetDirs(descriptorDirs)));
            log.debug("Descriptor Dirs: {}", Arrays.toString(descriptorDirs));
        } else {
            log.error("Empty Descriptor directories array");
        }
    }

    public void setDataDirs(String[] dataDirs) {
        if (dataDirs.length > 0) {
            this.dataDirs = new HashSet<>(Arrays.asList(presetDirs(dataDirs)));
            log.debug("Data Dirs: {}", Arrays.toString(dataDirs));
        } else {
            log.error("Empty data directories array");
        }
    }

    public void changeFlowType(FlowType newFlowType) {
        boolean isExecute = this.flowType.isExecute();
        boolean isFromFile = this.flowType.isFromFile();
        this.flowType = newFlowType;
        this.flowType.setExecute(isExecute);
        this.flowType.setFromFile(isFromFile);
    }

    public SshWorker getSourceSession() {
        return sourceSession;
    }

    public void setWriteCsv(boolean writeCsv) {
        this.writeCsv = writeCsv;
    }

    public void setWriteScript(boolean writeScript) {
        this.writeScript = writeScript;
    }

    public void setLookForOrphans(boolean lookForOrphans) {
        if (lookForOrphans)
            this.lookForFull = false;
        this.lookForOrphans = lookForOrphans;
    }

    public void setLookForFull(boolean lookForFull) {
        if (lookForFull)
            this.lookForOrphans = false;
        this.lookForFull = lookForFull;
    }

    public void setDsValidityPeriod(int dsValidityPeriod) {
        this.dsValidityPeriod = dsValidityPeriod;
    }

    public void setDsFilesUpdateDelay(int seconds) {
        this.dsFilesUpdateDelay = seconds;
    }

    public void setDsLocalDir(String dsLocalDir) {
        this.dsLocalDir = dsLocalDir;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public void setResultDir(Path resultDir) {
        this.resultDir = resultDir;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public void setFilterList(List<String> filterList) {
        this.filterList = filterList;
    }

    public void setScriptPrefix(String scriptPrefix) {
        this.scriptPrefix = scriptPrefix;
    }

    public void setScriptPostfix(String scriptPostfix) {
        this.scriptPostfix = scriptPostfix;
    }

    public void setScriptExecutorThreadsCount(int scriptExecutorThreadsCount) {
        this.scriptExecutorThreadsCount = scriptExecutorThreadsCount;
    }

    public FlowType getFlowType() {
        return flowType;
    }

    private void logRunParameters() {
        StringBuilder message = new StringBuilder();

        message.append("Execution parameters:\nUpdating datasets from ");
        if (flowType == FlowType.LOCAL)
            message.append("local ");
        else
            message.append("server ");

        message.append("descriptor directories: ").append(String.join(", ", descriptorDirs)).append("\n");
        message.append("Use data directories: ").append(String.join(", ", dataDirs)).append("\n");
        message.append("Load datasets info to local directory: ").append(dsLocalDir).append("\n");
        message.append("Finding datasets that older ").append(dsValidityPeriod).append(" days\n");
        if (lookForOrphans)
            message.append("Looking for orphans datasets (without descriptor)\n");
        if (lookForFull)
            message.append("Looking for full datasets\n");
        if (exceptions != null)
            message.append("Exception list: ").append(String.join(", ", exceptions)).append("\n");
        if (filterList != null)
            message.append("Filter list: ").append(String.join(", ", filterList)).append("\n");
        if (regex != null)
            message.append("Use regular expression: ").append(regex).append("\n");
        if (writeCsv)
            message.append("Write csv\n");
        if (writeScript)
            message.append("Write script file\n");
        if (flowType == FlowType.REMOTE && flowType.isExecute())
            message.append("Execute script\n");
        if (flowType == FlowType.REMOTE && flowType.isExecute() && flowType.isFromFile())
            message.append("Execute script file\n");
        if (flowType == FlowType.LOCAL && flowType.isExecute())
            message.append("Remove dataset locally\n");
        if (writeCsv || writeScript) {
            message.append("Result dir: ").append(resultDir);
        }
        log.info(message);
    }
}
