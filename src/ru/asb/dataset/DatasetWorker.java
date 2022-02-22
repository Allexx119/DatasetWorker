package ru.asb.dataset;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.dataset.collectors.Collector;
import ru.asb.dataset.filters.Filter;
import ru.asb.dataset.updaters.Updater;

import java.io.*;
import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class DatasetWorker {
    private static final Logger log = LogManager.getLogger(DatasetWorker.class);

    private final String dsLocalDir;
    private final Collector dsCollector;
    private final Updater dsUpdater;

    public DatasetWorker(String dsLocalDir, Collector collector, Updater updater) {
        this.dsLocalDir = dsLocalDir;
        this.dsCollector = collector;
        this.dsUpdater = updater;
    }

    /**
     * Получить список файлов с информацией о дата-сетах.
     * Если файл отсутствует, метод догружает необходимый файл с сервера.
     * Если стоит флаг принудительного обновления, все файлы обновляются с сервера.
     * @param dsServerDirs - перечень папок с дата-сетами которые необходимо получить.
     * */
    public Collection<Path> getUpdatedDatasetsFiles(String... dsServerDirs) throws IOException, InterruptedException {
        assert (dsLocalDir != null);
        assert (dsServerDirs != null);

        Path localDir = Paths.get(dsLocalDir);
        if (Files.notExists(localDir))
            Files.createDirectories(localDir);

        dsUpdater.update(dsLocalDir, dsServerDirs);

        return Arrays.stream(dsServerDirs)
                .filter(Objects::nonNull)
                .filter(folderName -> !folderName.isEmpty())
                .map(folderName -> localDir.resolve(folderName.substring(1, folderName.length() - 1) + ".txt"))
                .collect(Collectors.toSet());
    }


    /**
     * Собирает датасеты из файлов с информацией о датасетах
     * */
    public void collectDatasets(Collection<Path> dsFiles, boolean descriptor) throws IOException, InterruptedException {
        dsCollector.collectDatasets(dsFiles, descriptor);
    }


    /**
     * Собирает группы из списка дата-сетов
     * */
    public List<Group> collectGroups(Collection<Dataset> datasets) throws InterruptedException {
        log.info("Collecting groups...");
        List<Group> groupList = new ArrayList<>();
        Map<String, SoftReference<Group>> groupMap = new HashMap<>();
        if (datasets != null) {
            for (Dataset dataset : datasets) {
                Group group = new Group(dataset);
                SoftReference<Group> groupSoftReference = groupMap.get(group.getName());
                if (groupSoftReference != null) {
                    Group updatedGroup = groupSoftReference.get();
                    if (updatedGroup != null) {
                        updatedGroup.merge(group);
                    }
                } else {
                    groupMap.put(group.getName(), new SoftReference<>(group));
                }
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }
        log.info("Groups count: {}", groupMap.values().size());

        for (SoftReference<Group> reference : groupMap.values()) {
            Group group = reference.get();
            if (group != null) {
                groupList.add(group);
            }
        }
        return groupList;
    }

//    /**
//     * Собирает группы из списка дата-сетов
//     * */
//    List<Group> collectGroups(Collection<Dataset> datasets) throws InterruptedException {
//        log.info("Collecting groups...");
//        List<Group> groups = new ArrayList<>();
//        int index;
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                Group group = new Group(dataset);
//                if ((index = groups.indexOf(group)) != -1) {
//                    groups.get(index).merge(group);
//                } else {
//                    groups.add(group);
//                }
//                if (Thread.interrupted()) throw new InterruptedException();
//            }
//        }
//        log.info("Groups count: {}", groups.size());
//        return groups;
//    }


//    /**
//     * Get list with expired datasets;
//     * */
//    List<Dataset> getExpired(Collection<Dataset> datasets, int days) {
//        log.info("Getting datasets that older {} day(s)", days);
//        List<Dataset> expiredDsList = new ArrayList<>();
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                if (dataset.isExpired(days)) expiredDsList.add(dataset);
//            }
//        }
//        log.info("Expired datasets count: {} | {} Gb", expiredDsList.size(), String.format("%6.3f", Util.getDsListSize(expiredDsList, Unit.GIGABYTE)));
//        return expiredDsList;
//    }
//
//    /**
//     * Возвращает список дата-сетов, которые не входят в список исключений
//     * */
//    List<Dataset> getAllowed(Collection<Dataset> datasets, Collection<String> exceptions) {
//        log.info("Getting allowed datasets...");
//        List<Dataset> allowedList = new ArrayList<>();
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                if (!dataset.inExceptions(exceptions)) allowedList.add(dataset);
//            }
//        }
//        log.info("Allowed datasets count: {} | {} Gb", allowedList.size(), String.format("%6.3f", Util.getDsListSize(allowedList, Unit.GIGABYTE)));
//        return allowedList;
//    }
//
//    /**
//     * Возвращает список дата-сетов, название которых подходят по шаблону регулярного выражения
//     * */
//    List<Dataset> getMatched(Collection<Dataset> datasets, String regex) {
//        log.info("Getting datasets that matches to regular expression: {}", regex);
//        List<Dataset> matchedList = new ArrayList<>();
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                if (dataset.matches(regex)) matchedList.add(dataset);
//            }
//        }
//        log.info("Matched datasets count: {} | {} Gb", matchedList.size(), String.format("%6.3f", Util.getDsListSize(matchedList, Unit.GIGABYTE)));
//        return matchedList;
//    }
//
//    List<Dataset> getFiltered(Collection<Dataset> datasets, List<String> filterList) {
//        log.info("Filtering datasets...");
//        List<Dataset> filteredList = new ArrayList<>();
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                if (dataset.inFilter(filterList)) filteredList.add(dataset);
//            }
//        }
//        log.info("Filtered datasets count: {} | {} Gb", filteredList.size(), String.format("%6.3f", Util.getDsListSize(filteredList, Unit.GIGABYTE)));
//        return filteredList;
//    }
//
//    /**
//     * Возввращает список дата-сетов сирот, т.е. без заголовочных файлов-дескрипторов
//     * */
//    List<Dataset> getOrphans(Collection<Dataset> datasets) {
//        log.info("Getting orphans...");
//        List<Dataset> orphansList = new ArrayList<>();
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                if (dataset.isOrphan()) orphansList.add(dataset);
//            }
//        }
//        log.info("Orphans count: {} | {} Gb", orphansList.size(), String.format("%6.3f", Util.getDsListSize(orphansList, Unit.GIGABYTE)));
//        return orphansList;
//    }
//
//    /**
//     * Возвращает список дата-сетов, у которых есть заголовочный файл-дескриптор
//     * */
//    List<Dataset> getFull(Collection<Dataset> datasets) {
//        log.info("Getting full datasets...");
//        List<Dataset> fullList = new ArrayList<>();
//        if (datasets != null) {
//            for (Dataset dataset : datasets) {
//                if (!dataset.isOrphan()) fullList.add(dataset);
//            }
//        }
//        log.info("Full datasets count: {} | {} Gb", fullList.size(), String.format("%6.3f", Util.getDsListSize(fullList, Unit.GIGABYTE)));
//        return fullList;
//    }

    public List<Dataset> getDatasets() {
        return this.dsCollector.getDatasets();
    }

    public Filter filter() {
        return new Filter(getDatasets());
    }
}
