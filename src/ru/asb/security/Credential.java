package ru.asb.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class Credential extends HashMap<String, Object> implements Serializable {
    private final String credentialPathString;
    private final static String EXTENSION = ".crd";
    public static Logger log = LogManager.getLogger(Credential.class);

    public Credential(Path credentialFile) {
        if (!credentialFile.toString().endsWith(EXTENSION))
            this.credentialPathString = credentialFile.toString() + EXTENSION;
        else
            this.credentialPathString = credentialFile.toString();
    }

    public void putEncrypted(String name, String original) {
        if (name != null) {
            if (original != null) {
                this.put(name, SecurityManager.getInstance().encrypt(original.getBytes()));
            } else {
                this.putOriginal(name, null);
            }
        }
    }

    public void putOriginal(String name, String original) {
        if (name != null) {
            this.put(name, original);
        }
    }

    public String get(String key) {
        if (this.containsKey(key)) {
            Object obj = super.get(key);
            if (obj instanceof SecurityManager.SecurityObject) {
                SecurityManager.SecurityObject securityObj = (SecurityManager.SecurityObject) obj;
                return new String(SecurityManager.getInstance().decrypt(securityObj));
            } else {
                return (String) obj;
            }
        }
        return "";
    }

    public static Credential load(Path credentialFile) {
        Credential credential = new Credential(credentialFile);
        try {
            if (Files.exists(credentialFile) && Files.isRegularFile(credentialFile)) {
                try (FileInputStream fileInputStream = new FileInputStream(credentialFile.toFile());
                    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                    Object obj = objectInputStream.readObject();
                    if (obj instanceof Credential) {
                        credential = (Credential) obj;
                    }
                }
                Files.delete(credentialFile);
                log.info("Credential {} successfully loaded", credentialFile.toString());
            } else {
                log.info("Credential {} not found", credentialFile.toString());
            }
        } catch (Exception e) {
            log.warn("Credential {} loading error | {}", credentialFile.toString(), e.toString());
        }
        return credential;
    }


    public void save() {
        if (!this.isEmpty()) {
            Path credentialFile = Paths.get(credentialPathString);
            Path credentialDirectory = credentialFile.getParent();
            try {
                if (Files.notExists(credentialDirectory)) Files.createDirectories(credentialDirectory);
                if (Files.exists(credentialFile)) Files.delete(credentialFile);
                try (FileOutputStream fileOutputStream = new FileOutputStream(credentialFile.toFile());
                     ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                     objectOutputStream.writeObject(this);
                     objectOutputStream.flush();
                }
                Files.setAttribute(credentialFile, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
                log.info("Credential {} saved", credentialPathString);
            } catch (IOException ioe) {
                log.error(ioe);
            }
        }
    }

    public boolean hasData() {
        return !super.isEmpty();
    }
}
