package ru.asb.dataset;


import ru.asb.Main;
import ru.asb.script.Scriptable;
import ru.asb.util.Unit;
import ru.asb.util.Util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dataset implements CsvWriteable, Scriptable, Comparable<Dataset> {
    private static String regex;
    private static int sizeGroup;
    private static int lastUsedGroup;
    private static int folderGroup;
    private static int nameGroup;
    private static int extensionGroup;
    private static int invocationGroup;
    private static boolean firstInit = true;

    private String descriptorFolder = null;
    private String name = null;
    private String extension = null;
    private LocalDateTime lastUsed = null;
    private final Map<String, StringBuilder> folderInvocationMap = new TreeMap<>();
    private long size = 0L;
    private boolean correct = true;

    public Dataset(String datasetPartString, boolean isDescriptor) {
        parse(datasetPartString, isDescriptor);
    }

    private void parse(String datasetPartString, boolean isDescriptor) {
        if (firstInit) {
            regex = Main.getSystemProperties().getProperty("ds.parser.regex");
            sizeGroup = Integer.parseInt(Main.getSystemProperties().getProperty("ds.parser.group.size"));
            lastUsedGroup = Integer.parseInt(Main.getSystemProperties().getProperty("ds.parser.group.lastUse"));
            folderGroup = Integer.parseInt(Main.getSystemProperties().getProperty("ds.parser.group.folder"));
            nameGroup = Integer.parseInt(Main.getSystemProperties().getProperty("ds.parser.group.name"));
            extensionGroup = Integer.parseInt(Main.getSystemProperties().getProperty("ds.parser.group.extension"));
            invocationGroup = Integer.parseInt(Main.getSystemProperties().getProperty("ds.parser.group.invocation"));
            firstInit = false;
        }

        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(datasetPartString);
        if (matcher.find()) {
            String sizeStr = matcher.group(sizeGroup);
            if (sizeStr != null) this.size = Long.parseLong(sizeStr);

            String lastUsedStr = matcher.group(lastUsedGroup);
            if (lastUsedStr != null) {
                lastUsedStr = lastUsedStr.replaceAll("\\s+", " ");
                try {
                    DateTimeFormatter dtf = new DateTimeFormatterBuilder().appendPattern("MMM d[d] HH:mm").parseDefaulting(ChronoField.YEAR, LocalDateTime.now().getYear()).toFormatter(Locale.ENGLISH);
                    this.lastUsed = LocalDateTime.parse(lastUsedStr, dtf);
                    if (this.lastUsed.isAfter(LocalDateTime.now())) {
                        dtf = new DateTimeFormatterBuilder().appendPattern("MMM d[d] HH:mm").parseDefaulting(ChronoField.YEAR, LocalDateTime.now().getYear() - 1).toFormatter(Locale.ENGLISH);
                        this.lastUsed = LocalDateTime.parse(lastUsedStr, dtf);
                    }
                } catch (DateTimeParseException e) {
                    DateTimeFormatter dtf = new DateTimeFormatterBuilder().appendPattern("MMM d[d] yyyy").parseDefaulting(ChronoField.HOUR_OF_DAY, 0).toFormatter(Locale.ENGLISH);
                    this.lastUsed = LocalDateTime.parse(lastUsedStr, dtf);
                }
            }

            StringBuilder invocations = new StringBuilder();
            String invocation = matcher.group(invocationGroup);
            if (invocation != null)
                if (invocations.toString().isEmpty())
                    invocations.append(invocation.trim());
                else
                    invocations.append('|').append(invocation.trim());

            String folder = matcher.group(folderGroup);
            if (folder != null) {
                if (isDescriptor)
                    this.descriptorFolder = folder;
                else
                    this.folderInvocationMap.put(folder, invocations);
            }

            String potentialName = matcher.group(nameGroup).trim();
            if (potentialName.isEmpty()) {
                this.correct = false;
                this.name = "Incorrect dataset name";
            } else {
                this.name = potentialName;
            }

            this.extension = matcher.group(extensionGroup);
        } else {
            this.correct = false;
        }
    }

    public void merge(Dataset dataset) {
        if (this.isCorrect() && dataset.isCorrect() && this.name.equals(dataset.name)) {
            for (Map.Entry<String, StringBuilder> pair : dataset.folderInvocationMap.entrySet()) {
                if (this.folderInvocationMap.containsKey(pair.getKey())) {
                    this.folderInvocationMap.get(pair.getKey()).append('|').append(pair.getValue());
                } else {
                    this.folderInvocationMap.put(pair.getKey(), pair.getValue());
                }
            }
            if (this.lastUsed.isBefore(dataset.lastUsed)) {
                this.lastUsed = dataset.lastUsed;
            }
            if (this.descriptorFolder == null && dataset.descriptorFolder != null) {
                this.descriptorFolder = dataset.descriptorFolder;
            }
            this.size += dataset.size;
        }
    }

    public long size() {
        return this.size;
    }

    public List<String> getDataFolders() {
        LinkedList<String> foldersList = new LinkedList<>(folderInvocationMap.keySet());
        Collections.sort(foldersList);
        return foldersList;
    }

    public String getDescriptorFolder() {
        return descriptorFolder;
    }

    public List<String> getAllFolders() {
        List<String> foldersList = getDataFolders();
        if (descriptorFolder != null && !descriptorFolder.isEmpty())
            foldersList.add(0, descriptorFolder);
        return foldersList;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public boolean isCorrect() {
        return correct;
    }

    public boolean inFilter(List<String> filterList) {
        for (String filter : filterList) {
            if (this.name.equals(filter))
                return true;
        }
        return false;
    }

    /**
     * Is dataset in Exceptions
     * */
    public boolean inExceptions(Collection<String> exceptions) {
        for (String regex : exceptions) {
            Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check is dataset in Exceptions
     * */
    public boolean inExceptions(String[] exceptions) {
        return inExceptions(Arrays.asList(exceptions));
    }

    /**
     * Check is dataset expired
     * */
    public boolean isExpired(int validityPeriodInDays) {
        return (LocalDateTime.now().minusDays(validityPeriodInDays)).isAfter(getLastUsed());
    }

    /**
     * @return true if dataset doesn't have descriptor file; false if dataset has descriptor file.
     * */
    public boolean isOrphan() {
        return descriptorFolder == null;
    }

    /**
     * Is dataset match the regex
     * */
    public boolean matches(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name+extension).find();
    }

    public String getName() {
        return this.name;
    }

    public String getFullName() {
        return this.name + this.extension;
    }

    @Override
    public String toString() {
        return getCsvRow();
    }

    @Override
    public String getCsvRow() {
        if (this.isCorrect()) {
            String stringSize = String.format("%-10.3f", Util.size(size, Unit.MEGABYTE));
            return String.format("\"%s\";\"%s\";\"%s\";\"%s\"", Arrays.toString(getAllFolders().toArray(new String[0])), name + extension, stringSize, lastUsed.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));//, new Script(this).inline().toString());
        } else {
            return "";
        }
    }

    @Override
    public String getCsvHeader() {
        return "\"Folders\";\"Name\";\"Size, mb\";\"Last use date\"";
    }

    @Override
    public Collection<String> getScriptRows(String prefix, String postfix) {
        Collection<String> scriptRows = new ArrayList<>();
        if (this.isCorrect()) {
            if (prefix == null)
                prefix = "";
            if (!prefix.isEmpty())
                prefix = prefix.trim() + " ";
            if (postfix == null)
                postfix = "";
            if (!postfix.isEmpty())
                postfix = " " + postfix.trim();

            if (this.descriptorFolder != null && !this.descriptorFolder.isEmpty()) {
                scriptRows.add(String.format("%s'%s%s%s'%s;",prefix, descriptorFolder, name, extension, postfix));
            }
            for (Map.Entry<String, StringBuilder> pair : folderInvocationMap.entrySet()) {
                String folder = pair.getKey();
                String[] invocations = pair.getValue().toString().split("\\|");
                for (String invocation : invocations) {
                    if (!invocation.isEmpty() && !invocation.matches("\\s+"))
                        scriptRows.add(String.format("%s'%s%s%s%s'%s;",prefix, folder, name, extension, invocation, postfix));
                }
            }
        }
        return scriptRows;
    }

    public Collection<Path> getPaths() {
        Collection<Path> dsPaths = new ArrayList<>();
        if (this.descriptorFolder != null && !this.descriptorFolder.isEmpty()) {
            dsPaths.add(Paths.get(descriptorFolder + name + extension));
        }
        for (Map.Entry<String, StringBuilder> pair : folderInvocationMap.entrySet()) {
            String folder = pair.getKey();
            String[] invocations = pair.getValue().toString().split("\\|");
            for (String invocation : invocations) {
                if (!invocation.isEmpty() && !invocation.matches("\\s+"))
                    dsPaths.add(Paths.get(folder + name + extension + invocation));
            }
        }
        return dsPaths;
    }

    /**
     * Дата-сеты одинаковые если их имя одинаковое
     * */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dataset dataset = (Dataset) o;
        return name.equals(dataset.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(Dataset dataset) {
        return this.name.compareTo(dataset.name);
    }
}

