package edu.uw.ubicomplab.androidaccelapp;

import android.content.Context;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.Instances;

public class Model {
    // Examples of how the .arff format:
    // https://www.programcreek.com/2013/01/a-simple-machine-learning-example-in-java/
    // https://www.cs.waikato.ac.nz/~ml/weka/arff.html
    private Map<String, ArrayList<String[]>> trainingData;
    private String[] testData;
    public Map<String, String> featureNames;
    private String trainDataFilepath = "trainData.arff";
    private String testDataFilepath = "testData.arff";
    private Classifier model;
    private Context context;

    // TODO optional: give your gestures more informative names
    public String[] outputClasses = {"Gesture1", "Gesture2", "Gesture3"};

    public Model(Context context) {
        this.context = context;
        resetTrainingData();

        // Specify the features
        featureNames = new TreeMap<>();
        // TODO optional: create more features with more informative names
        featureNames.put("Feature1", "numeric");
        featureNames.put("Feature2", "numeric");
        featureNames.put("Feature3", "numeric");
    }

    /**
     * Add a sample to the training or testing set with the corresponding label
     * @param data: your features
     * @param outputLabel: the label for the data
     * @param isTraining: whether the sample should go into the train or test set
     */
    public void addFeatures(double[] data, String outputLabel, boolean isTraining) {
        // Convert the feature vector to Strings
        String[] stringData = new String[featureNames.keySet().size()];
        for (int i=0; i<featureNames.keySet().size(); i++) {
            stringData[i] = Double.toString(data[i]);
        }

        // Add to the list of feature samples as strings
        if (isTraining) {
            ArrayList<String[]> currentSamples = trainingData.get(outputLabel);
            currentSamples.add(stringData);
            trainingData.put(outputLabel, currentSamples);
        }
        else {
            testData = stringData;
        }
    }

    /**
     * Clears all of the data for the model
     */
    public void resetTrainingData() {
        // Create a blank list for each gesture
        trainingData = new LinkedHashMap<>();
        for (String s: outputClasses) {
            trainingData.put(s, new ArrayList<String[]>());
        }
    }

    /**
     * Returns the number of training samples for the given class index
     * @param index: the class index
     * @return the number of samples for the given class index
     */
    public int getNumTrainSamples(int index) {
        String className = outputClasses[index];
        return trainingData.get(className).size();
    }

    /**
     * Create an .arff file for the dataset
     * @param isTraining: whether the data is training or testing data
     */
    private void createDataFile(boolean isTraining) {
        PrintWriter writer;
        // Setup the file writer depending on whether it is training or testing data
        if (isTraining)
            writer = createPrintWriter(trainDataFilepath);
        else
            writer = createPrintWriter(testDataFilepath);

        // Name the dataset
        writer.println("@relation gestures");
        writer.println("");

        // Define the features
        for (String s: featureNames.keySet()) {
            writer.println("@attribute "+s+" "+featureNames.get(s));
        }

        // Define the possible output classes
        String outputOptions = "@attribute gestureName {";
        for (String s: outputClasses) {
            outputOptions += s+", ";
        }
        outputOptions = outputOptions.substring(0, outputOptions.length()-2);
        outputOptions += "}";
        writer.println(outputOptions);
        writer.println("");

        // Write the data
        writer.println("@data");
        if (isTraining) {
            // Go through each category of possible outputs and save their samples
            for (String s: outputClasses) {
                ArrayList<String[]> gestureSamples = trainingData.get(s);
                for (String[] sampleData: gestureSamples) {
                    String sample = "";
                    for (String x: sampleData) {
                        sample += x+",";
                    }
                    sample += s;
                    writer.println(sample);
                }
            }
        }
        else {
            // Write the new sample with a blank label
            String sample = "";
            for (String x: testData) {
                sample += x+",";
            }
            sample += "?";
            writer.println(sample);
        }
        writer.close();
    }

    /**
     * Trains a model for the training data
     */
    public void train() {
        // Create the file for training
        createDataFile(true);

        // Read the file and specify the last index as the class
        Instances trainInstances = createInstances(trainDataFilepath);
        if (trainInstances == null) {
            return;
        }
        trainInstances.setClassIndex(trainInstances.numAttributes()-1);

        // Define the classifier
        // TODO optional: try out different classifiers provided by Weka
        model = new J48();
        try {
            model.buildClassifier(trainInstances);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * Returns the string label for the recently tested gesture
     * @return the string label
     */
    public String test() {
        // Create the file for testing
        createDataFile(false);

        // Read the file and specify the last index as the class
        Instances testInstances = createInstances(testDataFilepath);
        testInstances.setClassIndex(testInstances.numAttributes()-1);

        // Predict
        String classLabel = null;
        try {
            double classIndex = model.classifyInstance(testInstances.instance(0));
            classLabel = testInstances.classAttribute().value((int) classIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classLabel;
    }

    /**
     * Reads the .arff file and converts it into an Instances object
     * @param filename the filepath for the .arff file
     * @return a newly created Instances object
     */
    private Instances createInstances(String filename) {
        // Read the file
        File SDFile = android.os.Environment.getExternalStorageDirectory();
        String fullFileName = SDFile.getAbsolutePath() + File.separator + filename;
        BufferedReader dataReader;
        try {
            FileReader fileReader = new FileReader(fullFileName);
            dataReader = new BufferedReader(fileReader);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        // Create the training instance
        Instances instances;
        try {
            instances = new Instances(dataReader);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context,
                    "Something is wrong with your .arff file!",
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        return instances;
    }

    /**
     * Creates the file at the location
     * @param filename: the filename that appears at the root of external storage
     * @return writer: the PrintWriter object to be used
     */
    public PrintWriter createPrintWriter(String filename) {
        // Create the file
        File SDFile = android.os.Environment.getExternalStorageDirectory();
        String fullFileName = SDFile.getAbsolutePath() + File.separator + filename;
        PrintWriter writer;
        try {
            writer = new PrintWriter(fullFileName);
        } catch(FileNotFoundException e) {
            return null;
        }
        return writer;
    }
}
