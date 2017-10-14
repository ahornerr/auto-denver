/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package codes.horner.whatthehackinference.tasks;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import codes.horner.whatthehackinference.Model;

public class LoadModelsTask {

    public static final String MODEL_DLC_FILE_NAME = "model.dlc";
    public static final String MODEL_MEAN_IMAGE_FILE_NAME = "mean_image.bin";
    public static final String LABELS_FILE_NAME = "labels.txt";
    public static final String IMAGES_FOLDER_NAME = "images";
    public static final String RAW_EXT = ".raw";
    public static final String JPG_EXT = ".jpg";
    private static final String LOG_TAG = LoadModelsTask.class.getSimpleName();

    public static List<Model> loadModels(Context context) {
        final List<Model> result = new ArrayList<>();
        final File modelsRoot = context.getExternalFilesDir("models");
        if (modelsRoot != null) {
            result.addAll(createModels(modelsRoot));
        }

        return result;
    }

    private static Set<Model> createModels(File modelsRoot) {
        final Set<Model> models = new LinkedHashSet<>();
        for (File child : modelsRoot.listFiles()) {
            if (!child.isDirectory()) {
                continue;
            }
            try {
                models.add(createModel(child));
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to load model from model directory.", e);
            }
        }
        return models;
    }

    private static Model createModel(File modelDir) throws IOException {
        final Model model = new Model();
        model.name = modelDir.getName();
        model.file = new File(modelDir, MODEL_DLC_FILE_NAME);
        model.meanImage = new File(modelDir, MODEL_MEAN_IMAGE_FILE_NAME);
        final File images = new File(modelDir, IMAGES_FOLDER_NAME);
        if (images.isDirectory()) {
            model.rawImages = images.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith(RAW_EXT);
                }
            });
            model.jpgImages = images.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith(JPG_EXT);
                }
            });
        }
        model.labels = loadLabels(new File(modelDir, LABELS_FILE_NAME));
        return model;
    }

    private static String[] loadLabels(File labelsFile) throws IOException {
        final List<String> list = new LinkedList<>();
        final BufferedReader inputStream = new BufferedReader(
                new InputStreamReader(new FileInputStream(labelsFile)));
        String line;
        while ((line = inputStream.readLine()) != null) {
            list.add(line);
        }
        return list.toArray(new String[list.size()]);
    }
}
