package ru.asb.dataset.collectors;

import ru.asb.dataset.Dataset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface Collector {
    /**
     * Сбор датасетов
     * */
    void collectDatasets(Collection<Path> dsFiles, boolean descriptor) throws IOException, InterruptedException;

    /**
     * Получить список всех дата-сетов коллектора
     * */
    List<Dataset> getDatasets();
}
