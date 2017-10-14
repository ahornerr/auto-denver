/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package codes.horner.whatthehackinference.tasks;

import android.graphics.Bitmap;
import android.util.Pair;

import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import codes.horner.whatthehackinference.Model;
import codes.horner.whatthehackinference.utils.ImageUtils;

public class ClassifyImageTask {

    private static final String LOG_TAG = ClassifyImageTask.class.getSimpleName();

    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;

    public static final String OUTPUT_LAYER = "prob";

    public static List<String> classify(NeuralNetwork network, Bitmap bitmap, Model model) {
        final List<String> result = new ArrayList<>();

        final FloatTensor tensor = network.createFloatTensor(network.getInputTensorsShapes().get("data"));

        final int[] dimensions = tensor.getShape();
//        final FloatBuffer meanImage = ImageUtils.loadMeanImageIfAvailable(model.meanImage, tensor.getSize());
//        if (meanImage.remaining() != tensor.getSize()) {
//            return new String[0];
//        }

        final boolean isGrayScale = (dimensions[dimensions.length - 1] == 1);
        if (!isGrayScale) {
            ImageUtils.writeRgbBitmapAsFloat(bitmap, IMAGE_MEAN, IMAGE_STD, tensor);
        } else {
//            ImageUtils.writeGrayScaleBitmapAsFloat(bitmap, meanImage, tensor);
        }

        final Map<String, FloatTensor> inputs = new HashMap<>();
        inputs.put("data", tensor);

        final Map<String, FloatTensor> outputs = network.execute(inputs);
        for (Map.Entry<String, FloatTensor> output : outputs.entrySet()) {
            if (output.getKey().equals(OUTPUT_LAYER)) {
                for (Pair<Integer, Float> pair : ImageUtils.topK(1, output.getValue())) {
                    result.add(model.labels[pair.first]);
                    result.add(String.valueOf(pair.second));
                }
            }
        }
        return result;
    }

}
