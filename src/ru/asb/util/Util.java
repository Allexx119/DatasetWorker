package ru.asb.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.dataset.CsvWriteable;
import ru.asb.dataset.Dataset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {
    private final static Logger log = LogManager.getLogger(Util.class);

    public static double size(long size, Unit unit) {
        double unitSize = (double) size;
        for (int i = 0; i < unit.ordinal(); i++) {
            unitSize = unitSize / 1024.0;
        }
        return unitSize;
    }

    /**
     * returns folder path with / symbols changed to _ (underline)
     * */
    public static String getFileNameWithUnderlines(String filename) {
        int dotIndex = filename.indexOf('.');
        if (dotIndex > 0) {
            filename = filename.substring(0, dotIndex);
        }
        return filename.replaceAll("[\\/\\\\]", "_").toLowerCase();
    }

    public static String getScriptName(Collection<Path> files) {
        StringBuilder filenameBuilder = new StringBuilder();
        for (Path file : files) {
            String filename = file.getFileName().toString();
            int dotIndex = filename.indexOf('.');
            if (dotIndex > 0) {
                filename = filename.substring(0, dotIndex);
            }
            filenameBuilder.append(filename).append('_');
        }
        return filenameBuilder.substring(0, filenameBuilder.length()-1);
    }

    public static String inUnixStyle(Path path) {
        String unixPath = path.toString().replaceAll("[\\\\\\/]", "/");
        return unixPath;
    }

    public static Path getAnotherName(Path file) {
        Path dir = file.getParent();
        String filename = file.getFileName().toString();
        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = filename.substring(dotIndex);
            filename = filename.substring(0,dotIndex);
        }

        int version = 0;

        Matcher matcher = Pattern.compile("(.+)\\s*\\((\\d+)\\)").matcher(filename);
        if (matcher.find()) {
            filename = matcher.group(1).trim();
            version = Integer.parseInt(matcher.group(2));
        }

        Path anotherFileName = dir.resolve(String.format("%s (%d)%s", filename, ++version, extension));
        if (Files.exists(anotherFileName))
            anotherFileName = Util.getAnotherName(anotherFileName);

        return anotherFileName;
    }

    public static double getDsListSize(List<? extends Dataset> dsList, Unit unit) {
        long commonSize = 0;
        for (Dataset dataset : dsList) {
            commonSize += dataset.size();
        }
        return size(commonSize, unit);
    }

    /**
     * Запись в CSV файл
     * */
    public static <T extends CsvWriteable> Path writeCSV(Path file, String header, List<T> list) throws IOException {
        Path directory = file.getParent();
        if (Files.notExists(directory)) Files.createDirectories(directory);
        try {
            Files.deleteIfExists(file);
            StringBuilder csvString = new StringBuilder(header).append(System.lineSeparator());
            for (T elem : list) {
                if (elem != null) csvString.append(elem.getCsvRow()).append(System.lineSeparator());
            }
            Files.write(file, csvString.toString().getBytes(), StandardOpenOption.CREATE);
        } catch (FileSystemException e) {
            file = Util.getAnotherName(file);
            writeCSV(file, header, list);
        }
        log.info("File {} is saved", file.toString());
        return file;
    }

    /**
     * Load current state of system.properties.
     * */
    public static void loadProperties(Properties properties, Path propertiesFile) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(Files.readAllBytes(propertiesFile));
            properties.load(byteArrayInputStream);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static Thread logDelayed(String message, int ms) {
        Thread delayThread = new Thread(() -> {
            try {
                Thread.sleep(ms);
                log.info(message);
            } catch (InterruptedException ignored) {}
        }, "delayedLogThread");
        delayThread.setDaemon(true);
        delayThread.start();
        return delayThread;
    }
}
