package com.newrelic.agent.android.util;

import android.content.Context;

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
    private static final int DEFAULT_MAX_OFFLINE_Storage_SIZE = 100 * 1024 * 1024; //MB
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static File offlineStorage;
    private static String offlineFilePath = "";
    private static int offlineStorageSize = 100 * 1024 * 1024; //MB

    public OfflineStorage(Context context) {
        try {
            offlineStorage = new File(context.getFilesDir(), OFFLINE_STORAGE);
            if (!offlineStorage.exists()) {
                offlineStorage.mkdirs();
            }
        } catch (Exception ex) {
            log.error("OfflineStorage: ", ex);
        }
    }

    public boolean persistHarvestDataToDisk(String data) {
        boolean isSaved = false;
        try {
            double totalData = getTotalFileSize() + data.getBytes().length;
            if (totalData > offlineStorageSize) {
                return false;
            }

            if (!offlineStorage.exists()) {
                offlineStorage.mkdirs();
            }

            File offlineFile = new File(offlineStorage.getAbsolutePath(), "payload_" + System.currentTimeMillis());
            if (!offlineFile.exists()) {
                offlineFile.createNewFile();
                setOfflineFilePath(offlineFile.getAbsolutePath());
            }

            FileWriter fw = new FileWriter(offlineFile, true);
            BufferedWriter buf = new BufferedWriter(fw);
            buf.write(data);
            buf.close();
            fw.close();
            isSaved = true;
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
            isSaved = false;
        }
        return isSaved;
    }

    public Map<String, String> getAllOfflineData() {
        Map<String, String> harvestDataObjects = new HashMap<String, String>();
        try {
            if (offlineStorage == null) {
                return harvestDataObjects;
            }

            File[] files = offlineStorage.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    BufferedReader in = null;
                    try {
                        in = new BufferedReader(new FileReader(files[i]));
                        String harvestDataFromFile = in.readLine();
                        harvestDataObjects.put(files[i].getAbsolutePath(), harvestDataFromFile);
                    } catch (Exception e) {
                        log.error("OfflineStorage: ", e);
                    }

                }
            }
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
        }
        return harvestDataObjects;
    }

    public double getTotalFileSize() {
        double totalSizeInBytes = 0.0;
        try {
            if (offlineStorage == null) {
                return 0;
            }

            File[] files = offlineStorage.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    double fileInBytes = files[i].length();
                    totalSizeInBytes += fileInBytes;
                }
            }
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
        }
        return totalSizeInBytes;
    }

    public void cleanOfflineFiles() {
        try {
            File[] files = offlineStorage.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    files[i].delete();
                }
            }
        } catch (Exception e) {
            log.error("OfflineStorage: ", e);
        }
    }

    public static void setMaxOfflineStorageSize(int maxSize) {
        if (maxSize <= 0) {
            log.error("Offline storage size cannot be smaller than 0");
            maxSize = DEFAULT_MAX_OFFLINE_Storage_SIZE;
        }

        if (maxSize > DEFAULT_MAX_OFFLINE_Storage_SIZE) {
            log.info("Offline Storage size sets to" + DEFAULT_MAX_OFFLINE_Storage_SIZE);
        }

        offlineStorageSize = maxSize;
    }

    public File getOfflineStorage() {
        return offlineStorage;
    }

    public void setOfflineStorage(File offlineStorage) {
        OfflineStorage.offlineStorage = offlineStorage;
    }

    public String getOfflineFilePath() {
        return offlineFilePath;
    }

    public void setOfflineFilePath(String path) {
        offlineFilePath = path;
    }

    public int getOfflineStorageSize() {
        return offlineStorageSize;
    }

    public void setOfflineStorageSize(int maxSize) {
        offlineStorageSize = maxSize;
    }
}
