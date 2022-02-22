package ru.asb.dataset.filters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.asb.dataset.Dataset;
import ru.asb.util.Unit;
import ru.asb.util.Util;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Filter {
    private static final Logger log = LogManager.getLogger(Filter.class);
    private Stream<Dataset> stream;

    public Filter(List<Dataset> datasetList) {
        this.stream = datasetList.stream();
    }

    /**
     * Возвращает список с просроченными дата-сетами
     * */
    public Filter expired(int days) {
        log.info("Getting datasets that older {} day(s)", days);
        stream = stream.filter(dataset -> dataset.isExpired(days));
        return this;
    }

    /**
     * Возвращает список дата-сетов, которые не входят в список исключений
     * */
    public Filter notIn(Collection<String> exceptions) {
        if (exceptions != null && exceptions.size() > 0) {
            log.info("Check for exceptions...");
            stream = stream.filter(dataset -> !dataset.inExceptions(exceptions));
        }
        return this;
    }

    /**
     * Возвращает список дата-сетов, название которых подходят по шаблону регулярного выражения
     * */
    public Filter matched(String regex) {
        if (regex != null) {
            log.info("Getting datasets that matches to regular expression: {}", regex);
            stream = stream.filter(dataset -> dataset.matches(regex));
        }
        return this;
    }

    /**
     * Возввращает список дата-сетов сирот, т.е. без заголовочных файлов-дескрипторов
     * */
    public Filter orphans(boolean searchOrphans) {
        if (searchOrphans) {
            log.info("Getting orphans...");
            stream = stream.filter(Dataset::isOrphan);
        }
        return this;
    }

    /**
     * Возвращает список дата-сетов, у которых есть заголовочный файл-дескриптор
     * */
    public Filter full(boolean searchFull) {
        if (searchFull) {
            log.info("Getting full datasets...");
            stream = stream.filter(dataset -> !dataset.isOrphan());
        }
        return this;
    }

    /**
     * Возвращает список дата-сетов, которые есть в списке
     * */
    public Filter onlyIn(List<String> filterList) {
        if (filterList != null && filterList.size() > 0) {
            log.info("Filtering datasets...");
            stream = stream.filter(dataset -> dataset.inFilter(filterList));
        }
        return this;
    }

    public List<Dataset> getList() {
        List<Dataset> resultList = stream.collect(Collectors.toList());
        log.info("Filtered datasets count: {} | {} Gb", resultList.size(), String.format("%6.3f", Util.getDsListSize(resultList, Unit.GIGABYTE)));
        return resultList;
    }
}
