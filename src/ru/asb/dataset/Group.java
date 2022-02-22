package ru.asb.dataset;


import ru.asb.script.Scriptable;
import ru.asb.util.Unit;
import ru.asb.util.Util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Класс Group группирует дата-сеты по названию, игнорирую изменяемые части такие как TASK_ID и LOADING_DT. То есть группирует по шаблону.
 * */
public class Group implements CsvWriteable, Comparable<Group>, Scriptable {
    private static final Pattern groupPattern = Pattern.compile("(?:(.+?)(?:TASK(?:_)?ID(?:_)?(\\d+)))?(.+?)_?(\\d{4}-\\d{2}-\\d{2})?(_?REJ)?(?:[\\.|_]ds\\b)", Pattern.CASE_INSENSITIVE);
    private final Set<LocalDate> dateSet;
    private final Set<Integer> taskIdSet;
    private final List<Dataset> datasetList;
    private String name;

    Group(Dataset dataset) {
        this.dateSet = new HashSet<>();
        this.taskIdSet = new HashSet<>();
        this.datasetList = new ArrayList<>();

        Matcher matcher = groupPattern.matcher(dataset.getFullName());
        if (matcher.find()) {
            StringBuilder groupNameStringBuilder = new StringBuilder();
            if (matcher.group(1) != null) groupNameStringBuilder.append(matcher.group(1));
            if (matcher.group(2) != null) {
                groupNameStringBuilder.append("#TASK_ID#");
                this.taskIdSet.add(Integer.parseInt(matcher.group(2)));
            }
            if (matcher.group(3) != null) groupNameStringBuilder.append(matcher.group(3));
            if (matcher.group(4) != null) {
                groupNameStringBuilder.append("_#LOADING_DT#");
                this.dateSet.add(LocalDate.parse(matcher.group(4), DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)));
            }
            if (matcher.group(5) != null) groupNameStringBuilder.append(matcher.group(5));
        //    if (matcher.group(6) != null) extension = matcher.group(6);
            this.name = groupNameStringBuilder.toString();
        }
        this.datasetList.add(dataset);
    }

    /**
     * Объединяет группы по названию группы.
     * */
    public void merge(Group group) {
        if (this.name.equals(group.name)) {
            this.taskIdSet.addAll(group.taskIdSet);
            this.dateSet.addAll(group.dateSet);
            this.datasetList.addAll(group.datasetList);
        }
    }

    /**
     * Return group size in determined Unit
     * */
    private long size() {
        long _size = 0L;
        for (Dataset dataset : datasetList) {
            _size += dataset.size();
        }
        return _size;
    }

    public List<String> getFolders() {
        Set<String> descriptorSet = new HashSet<>();
        Set<String> folderSet = new HashSet<>();
        for (Dataset dataset : datasetList) {
            folderSet.addAll(dataset.getDataFolders());
            descriptorSet.add(dataset.getDescriptorFolder());
        }
        List<String> folders = new LinkedList<>(folderSet);
        Collections.sort(folders);
        if (!descriptorSet.isEmpty())
            folders.addAll(0, descriptorSet);
        return folders;
    }

    public List<Dataset> getDatasets() {
        return datasetList;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Groups comparing by name;
     * Groups with same group name should by in one group.
     * */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return Objects.equals(name, group.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return getCsvRow();
    }

    @Override
    public String getCsvHeader() {
        return "\"Folders\";\"Group name\";\"Dates in DS name\";\"Task ID\";\"Group size, mb\"";
    }

    @Override
    public String getCsvRow() {
        String stringSize = String.format("%-10.3f", Util.size(size(), Unit.MEGABYTE));
        return String.format("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"", this.getFolders().toString(), name, dateSet.toString(), taskIdSet.toString(), stringSize);
    }

    @Override
    public int compareTo(Group anotherGroup) {
        long diff = this.size() - anotherGroup.size();
        if (diff == 0) {
           return this.name.compareTo(anotherGroup.name);
        }
        return (int)diff;
    }

    @Override
    public Collection<String> getScriptRows(String prefix, String postfix) {
        Collection<String> rows = new ArrayList<>();
        for (Dataset dataset : datasetList) {
            rows.addAll(dataset.getScriptRows(prefix, postfix));
        }
        return rows;
    }
}


