package com.newrelic.agent.android.util;

import android.os.Environment;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class OfflineStorage {
    private static final String OFFLINE_STORAGE = "nr_offline_storage";
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static File offlineStorage = new File(Environment.getDataDirectory(), OFFLINE_STORAGE);
    private static String offlineFilePath = "";

    public OfflineStorage() {
        try {
            if (!offlineStorage.exists()) {
                offlineStorage.mkdirs();
            }

            File offlineFile = new File(offlineStorage.getAbsolutePath(), "payload_" + System.currentTimeMillis());
            if (!offlineFile.exists()) {
                offlineFile.createNewFile();
                setOfflineFilePath(offlineFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            log.error("OfflineStorage: ", ex);
        }
    }

    public static boolean persistDataToDisk(String data) {
        boolean isSaved = false;
        try {
            if (!offlineStorage.exists()) {
                offlineStorage.mkdirs();
            }

            File offlineFile = new File(offlineStorage.getAbsolutePath(), "payload_" + System.currentTimeMillis());
            if (!offlineFile.exists()) {
                offlineFile.createNewFile();
                setOfflineFilePath(offlineFile.getAbsolutePath());
            }

            BufferedWriter buf = new BufferedWriter(new FileWriter(offlineFile, true));
            buf.append(data);
            isSaved = true;
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
            isSaved = false;
        }
        return isSaved;
    }

    public static Map<String, String> getAllOfflineData() {
        Map<String, String> harvestDataObjects = new HashMap<String, String>();
        try {
            if(offlineStorage == null){
                return harvestDataObjects;
            }

            File[] files = offlineStorage.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length - 1; i++) {
                    BufferedReader in = null;
                    try {
                        in = new BufferedReader(new FileReader(files[i]));
                        String harvestDataFromFile = in.readLine();
                        harvestDataObjects.put(files[i].getAbsolutePath(), harvestDataFromFile);
                    } catch (Exception e){
                        log.error("OfflineStorage: ", e);
                    }

                }
            }
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
        }
        return harvestDataObjects;
    }

    public static void cleanOfflineFiles() {
        try {
            File[] files = offlineStorage.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length - 1; i++) {
                    files[i].deleteOnExit();
                }
            }
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
        }
    }

    public static String getOfflineFilePath() {
        return offlineFilePath;
    }

    public static void setOfflineFilePath(String offlineFilePath) {
        offlineFilePath = offlineFilePath;
    }
}
