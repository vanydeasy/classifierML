/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package myclassifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.trees.Id3;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;

/**
 *
 * @author Venny
 */
public class MyClassifier {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, Exception {
        // Load data from ARFF or CSV
        Instances data;
        if (args[0].substring(args[0].lastIndexOf(".") + 1).equals("arff")){
            BufferedReader br = new BufferedReader(new FileReader(args[0]));
            ArffLoader.ArffReader arff = new ArffLoader.ArffReader(br);
            data = arff.getData();
        } else {
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(args[0]));
            data = loader.getDataSet();
        }
        
        // Build Naive Bayes Model
        data.setClassIndex(data.numAttributes() - 1);
        NaiveBayes model = new NaiveBayes();
        model.buildClassifier(data);
        System.out.println(model.toString());
        
        // Save Model
        weka.core.SerializationHelper.write(args[0].substring(0, args[0].indexOf('.')) + "_naivebayes.model", model);
        
        // Load Model
        Classifier cls = (Classifier) weka.core.SerializationHelper.read(args[0].substring(0, args[0].indexOf('.')) + "_naivebayes.model");
        
        // 10-fold Cross Validation Evaluation
        Evaluation eval = new Evaluation(data);
        eval.crossValidateModel(cls, data, 10, new Random());
        System.out.println(eval.toSummaryString("\n\n\n\nNaive Bayes 10-Fold Cross Validation\n============\n", false));
    }
    
}
