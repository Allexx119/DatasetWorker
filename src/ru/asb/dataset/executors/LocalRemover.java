package ru.asb.dataset.executors;

import ru.asb.dataset.Dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class LocalRemover extends Executor {
    List<Dataset> datasets;

    public LocalRemover(List<Dataset> datasets) {
        this.datasets = datasets;
    }

    @Override
    public void run() {
        datasets.stream().flatMap(dataset -> dataset.getPaths().stream()).parallel().forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });
    }
}
