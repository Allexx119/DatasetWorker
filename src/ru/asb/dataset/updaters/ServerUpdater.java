package ru.asb.dataset.updaters;

import ru.asb.Main;
import ru.asb.ssh.SshWorker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ServerUpdater extends Updater {
    private final SshWorker sshWorker;
    public ServerUpdater(SshWorker sshWorker, int dsFilesUpdateDelay) {
        super(dsFilesUpdateDelay);
        this.sshWorker = sshWorker;
    }

    @Override
    public void update(String toLocalDir, String... fromServerDirs) throws InterruptedException, IOException {
        Path dsLocalDir = Paths.get(toLocalDir);
        List<String> dsServerDirsList = getDsServerDirsToUpdate(dsLocalDir, fromServerDirs);
        if (dsServerDirsList.size() > 0) {
            if (!sshWorker.sessionIsOpen()) sshWorker.openSession();
            ExecutorService getDatasetExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
                int count = 1;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "UpdateDatasetThread-" + count++);
                }
            });
            for (String dirName : dsServerDirsList) {
                String command = String.format(Main.getSystemProperties().getProperty("ssh.command.template"), dirName);
                Path dsFile = dsLocalDir.resolve(dirName.substring(1, dirName.length() - 1) + ".txt");
                getDatasetExecutor.submit(() -> {
                    try {
                        if (Files.notExists(dsFile.getParent())) Files.createDirectories(dsFile.getParent());
                        FileOutputStream resultOutputStream = new FileOutputStream(dsFile.toFile());
                        sshWorker.execute(command, resultOutputStream);
                        resultOutputStream.close();
                    } catch (Exception e) {
                        log.error("Error executing command: {} | {} | {}", command, e, Arrays.toString(e.getStackTrace()));
                    }
                });
            }
            getDatasetExecutor.shutdown();
            while (!getDatasetExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                log.warn("Executors await termination timeout is elapsed");
            }
            log.info("Datasets {} updated", Arrays.toString(fromServerDirs));
        }
    }

    /**
     * Получить список обновляемых директорий с сревера
     * Алгоритм
     * 1. Найти файлы в локальной директории, которые соответствуют серверным директориям
     * 2. Для найденных файлов получить дату изменения.
     * 3. Если дата изменения - текущая дата > expirationTime, добавить серверную директорию в список обновляемых
     * 4. Если файл отсутствует - добавить серверную директорию в список обновляемых
     * */
    private List<String> getDsServerDirsToUpdate(Path dsLocalDir, String... dsServerDirs) throws IOException {
        if (dsFilesUpdateDelay == 0) {
            return Arrays.asList(dsServerDirs);
        }
        List<String> dsServerDirsUpdateList = new ArrayList<>();
        for (String dirName : dsServerDirs) {
            Path dsFile = dsLocalDir.resolve(dirName.substring(1, dirName.length() - 1) + ".txt");
            if (Files.exists(dsFile)) {
                Instant expirationTime = Files.getLastModifiedTime(dsFile).toInstant().plusSeconds(dsFilesUpdateDelay);
                if (Instant.now().compareTo(expirationTime) >= 0) {
                    dsServerDirsUpdateList.add(dirName);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
                    log.info("Updating datasets from {} : {}. When expired? At {} ", dirName, dsFile, formatter.format(expirationTime));
                }
            } else {
                dsServerDirsUpdateList.add(dirName);
            }
        }
        return dsServerDirsUpdateList;
    }

}
