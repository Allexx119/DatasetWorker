package ru.asb.script;

import java.util.Collection;

@FunctionalInterface
public interface Scriptable {
 //   String getScriptName();
    Collection<String> getScriptRows(String prefix, String postfix);
}
