package ru.asb.dataset.collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.dataset.Dataset;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.*;

public class DatasetSoftReferenceMapCollector implements Collector {
    private Map<String, SoftReference<Dataset>> datasets;
    private static final Logger log = LogManager.getLogger(DatasetMapCollector.class);

    public DatasetSoftReferenceMapCollector() {
        this.datasets = new TreeMap<>();
    }

    @Override
    public void collectDatasets(Collection<Path> dsFiles, boolean descriptor) throws IOException, InterruptedException {
        if (dsFiles != null) {
            for (Path dsFile : dsFiles) {
                log.info("Collecting datasets from file {}", dsFile.toString());
                BufferedReader bufferedReader = new BufferedReader(new FileReader(dsFile.toFile()));
                String row;
                while ((row = bufferedReader.readLine()) != null) {
                    Dataset dataset = new Dataset(row, descriptor);
                    if (dataset.isCorrect()) {
                        SoftReference<Dataset> dsSoftReference = datasets.get(dataset.getName());
                        if (dsSoftReference != null) {
                            Dataset updatedDs = dsSoftReference.get();
                            if (updatedDs != null) {
                                updatedDs.merge(dataset);
                            }
                        } else {
                            datasets.put(dataset.getName(), new SoftReference<>(dataset));
                        }
                    }
                    if (Thread.interrupted()) throw new InterruptedException();
                }
                bufferedReader.close();
            }
        }
    }

    @Override
    public List<Dataset> getDatasets() {
        List<Dataset> dsList = new ArrayList<>();
        for (SoftReference<Dataset> reference : datasets.values()) {
            Dataset ds = reference.get();
            if (ds != null) {
                dsList.add(ds);
            }
        }
        return dsList;
    }
}
