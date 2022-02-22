package ru.asb.dataset.updaters;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

//TODO - need tests
public class LocalUpdater extends Updater {
    public LocalUpdater(int dsFilesUpdateDelay) {
        super(dsFilesUpdateDelay);
    }

    @Override
    public void update(String toDir, String... fromDirs) throws InterruptedException, IOException  {
        Path dsToDir = Paths.get(toDir);
        if (Files.notExists(dsToDir))
            Files.createDirectories(dsToDir);
        if (fromDirs.length > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd HH:mm");
            ExecutorService getDatasetExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
                int count = 1;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "UpdateOnServerDatasetThread-" + count++);
                }
            });

            for (String fromDir : fromDirs) {
                StringBuilder dsFileBuilder = new StringBuilder();
                Path dsFromDir = Paths.get(fromDir);
                if (Files.exists(dsFromDir)) {
                    log.info("Updating datasets from {}", fromDir);
                    String fileName = dsFromDir.toString();
                    Path dsFile = dsToDir.resolve(fileName.substring(1, fileName.length() - 1) + ".txt");
                    getDatasetExecutor.submit(() -> {
                        try {
                            Files.walkFileTree(dsFromDir, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                    try {
                                        if (!Files.isRegularFile(file))
                                            return FileVisitResult.CONTINUE;
                                        dsFileBuilder.append("00000 00 ---------- 1 dsadm dstage ").append(Files.size(file)).append(formatter.format(Files.getLastModifiedTime(file).toInstant())).append("\n");
                                    } catch (IOException ignored) {
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } catch (IOException ioe) {
                            log.error("Error walking files tree: {} | {}", ioe, Arrays.toString(ioe.getStackTrace()));
                        }
                    });

                    Files.write(dsFile, dsFileBuilder.toString().getBytes(), StandardOpenOption.CREATE);
                    getDatasetExecutor.shutdown();
                    while (!getDatasetExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                        log.warn("Executors await termination timeout is elapsed");
                    }
                } else {
                    log.warn("No directory: {}", dsFromDir);
                }
            }
            log.info("Datasets updated successfully");
        }
    }
}
