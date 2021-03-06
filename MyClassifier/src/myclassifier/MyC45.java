/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package myclassifier;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.DoubleStream;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

/**
 *
 * @author Venny
 */
public class MyC45 extends Classifier {
    private final double MISSING_VALUE = Double.NaN;
    private MyC45[] children; //node's successor
    private double label; //class value if node is leaf
    private Attribute splitAttr; //used for splitting
    private Attribute classAttr; //class attribute of dataset
    private double[] distribution; //class distribution for each label
    private double threshold;
    
    //returns default capabilities of the classifier
    @Override
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();
        
        result.enable(Capabilities.Capability.MISSING_VALUES);
        result.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        result.enable(Capabilities.Capability.MISSING_CLASS_VALUES);
        result.enable(Capabilities.Capability.NOMINAL_CLASS);
        result.setMinimumNumberInstances(0);
        
        return result;
    }
    
    // Computes the entropy of a dataset.
    private double computeEntropy(Instances data) throws Exception {
        double [] labelCounter = new double[data.numClasses()];
        for(int i=0; i<data.numInstances(); ++i){
            labelCounter[(int) data.instance(i).classValue()]++;
        }
        
        double entropy = 0;
        for (int i=0; i<labelCounter.length; ++i) {
            if (labelCounter[i] > 0) {
                double proportion = labelCounter[i]/data.numInstances();
                entropy -= (proportion)*Utils.log2(proportion);
            }
        }
        return entropy;
    }
    
    // Splits a dataset according to the values of a nominal attribute.
    private Instances[] splitNominalData(Instances data, Attribute att) {
        Instances[] splitData = new Instances[att.numValues()];
        for (int j = 0; j < att.numValues(); j++) {
            splitData[j] = new Instances(data, data.numInstances());
        }
        for (int i=0; i<data.numInstances(); ++i) {
            splitData[(int) data.instance(i).value(att)].add(data.instance(i));
        }

        for (int i=0; i<splitData.length; ++i) {
            splitData[i].compactify();
        }
        return splitData;
    }
    
    // Splits a dataset according to a numeric attribute's threashold
    private Instances[] splitNumericData(Instances data, Attribute att, Double threshold) {
        Instances[] splitData = new Instances[2];
        splitData[0] = new Instances(data, data.numInstances());
        splitData[1] = new Instances(data, data.numInstances());
        for (int i = 0; i < data.numInstances(); i++) {
            if (data.instance(i).value(att) <= threshold) {
                splitData[0].add(data.instance(i));
            } else {
                splitData[1].add(data.instance(i));
            }
        }
        splitData[0].compactify();
        splitData[1].compactify();
        return splitData;
    }
    
    // Computes information gain for a nominal attribute
    private double computeNominalIG(Instances data, Attribute att) throws Exception {
        double IG = computeEntropy(data);
        Instances[] splitData = splitNominalData(data, att);
        for (Instances splitdata : splitData) {
            if (splitdata.numInstances() > 0) {
                double splitNumInstances = splitdata.numInstances();
                double dataNumInstances = data.numInstances();
                double proportion = splitNumInstances / dataNumInstances;
                IG -= proportion * computeEntropy(splitdata);
            }
        }
        return IG;
    }
    
    // Computes information gain for a numeric attribute
    private double computeNumericIG(Instances data, Attribute att, Double threshold) throws Exception {
        double IG = computeEntropy(data);
        Instances[] splitData = splitNumericData(data, att, threshold);
        for (Instances splitdata : splitData) {
            if (splitdata.numInstances() > 0) {
                double splitNumInstances = splitdata.numInstances();
                double dataNumInstances = data.numInstances();
                double proportion = splitNumInstances / dataNumInstances;
                IG -= proportion * computeEntropy(splitdata);
            }
        }
        return IG;
    }
    
    
    // Calculate threshold for numeric attributes
    private double calculateThreshold(Instances data, Attribute att) throws Exception {
        // OPSI 1
        // Sort berdasarkan nilai atribut, tiap batas pergantian kelas di split dan dihitung IGnya
        // Dari semua kemungkinan tempat split, ambil yang IGnya paling besar
        data.sort(att);
        double threshold = data.instance(0).value(att);
        double IG = 0;
        for (int i = 0; i < data.numInstances()-1; i++){
            if (data.instance(i).classValue() != data.instance(i+1).classValue()) {
                double currentIG = computeNumericIG(data, att, data.instance(i).value(att));
                if (currentIG > IG) {
                    threshold = data.instance(i).value(att);
                }
            }
        } 
        return threshold;
        
        // OPSI 2
        // threshold = min+max/2
        /* double min = data.instance(0).value(att);
        double max = data.instance(0).value(att);
        for (int i=1; i< data.numInstances(); i++) {
            if (data.instance(i).value(att) < min) min = data.instance(i).value(att);
            if (data.instance(i).value(att) > max) max = data.instance(i).value(att);
        }
        return min+max/2; */
        
        // OPSI 3
        // threshold = avg
        /* double sum = 0;
        for (int i=1; i< data.numInstances(); i++) {
            sum += data.instance(i).value(att);
        }
        return sum/data.numInstances(); */
    }
    
    private double[] attributeThreshold(Instances data) throws Exception {
        double[] th = new double[data.numAttributes()];
        for (int i = 0; i< data.numAttributes(); i++) {
            if (data.attribute(i).isNumeric()) {
                th[i] = calculateThreshold(data, data.attribute(i));
            } else {
                th[i] = 0;
            }
        }
        return th;
    }
    
    // Replace missing value with most common value of the attr among other examples with same target value 
    private void handleMissingValue (Instances data) {
        for (int i = 0; i < data.numInstances(); i++) {
            for (int j = 0; j < data.numAttributes(); j++) {
                if (data.instance(i).isMissing(j)) { // jika value untuk atribut ke-j missing
                    data.instance(i).setValue(data.attribute(j), mostCommonValue(data, data.attribute(j), data.instance(i).classValue()));
                }
            }
        }
    }
    
    private double mostCommonValue (Instances data, Attribute att, Double classValue) {
        if (att.isNumeric()) {
            double sum = 0;
            for (int i=1; i< data.numInstances(); i++) {
                sum += data.instance(i).value(att);
            }
            return sum/data.numInstances();
        } else {
            List<String> valList = Collections.list(att.enumerateValues());
            int [] attCount = new int [att.numValues()];
            for (int i = 0; i < data.numInstances(); i++) {
                for (int j = 0; j < att.numValues(); j++) {
                    if (!data.instance(i).isMissing(att)) {
                        if (data.instance(i).stringValue(att).equals(valList.get(j)) && data.instance(i).classValue() == classValue) {
                            attCount[j]++; 
                        }
                    }
                }
            }
            int maxIndex = 0;
            int max = attCount[0];
            for (int j = 1; j < attCount.length; j++){
               if (attCount[j] > max) maxIndex = j;
            }
            System.out.println(valList.get(maxIndex));
            return att.indexOfValue(valList.get(maxIndex));
        }
    }
    
    //return the index with largest value from array
    private int maxIndex(double[] array) {
        double max=0;
        int index=0;
        if (array.length>0) {
            for (int i=0; i<array.length; ++i) {
                if (array[i]>max) {
                    max=array[i];
                    index=i;
                }
            }
            return index;
        } else {
            return -1;
        }
    }
    
    // Creates an Id3 tree.
    private void buildTree(Instances data) throws Exception {
        handleMissingValue(data);
        //cek apakah terdapat instance yang dalam node ini
        if (data.numInstances()==0) {
            splitAttr = null;
            label = MISSING_VALUE;
            distribution = new double[data.numClasses()];
        } else {
            //jika ada, menghitung IG maksimum
            double[] th = attributeThreshold(data);
            double[] infoGains = new double[data.numAttributes()];
            Enumeration attEnum = data.enumerateAttributes();
            while (attEnum.hasMoreElements()) {
                Attribute att = (Attribute) attEnum.nextElement();
                if (att.isNumeric()) {
                    infoGains[att.index()] = computeNumericIG(data, att, th[att.index()]);
                } else {
                    infoGains[att.index()] = computeNominalIG(data, att);
                }
            }
            //cek max IG
            int maxIG = maxIndex(infoGains);
            if (maxIG!=-1) { //kalo kosong
                splitAttr = data.attribute(maxIG);
                threshold = th[maxIG];
            } else {
                Exception exception = new Exception("array null");
                throw exception;
            }
            //Membuat daun jika IG-nya 0
            if (Double.compare(infoGains[splitAttr.index()], 0) == 0) {
                splitAttr = null;
                distribution = new double[data.numClasses()];
                for (int i=0; i<data.numInstances(); ++i) {
                    Instance inst = (Instance) data.instance(i);
                    distribution[(int) inst.classValue()]++;
                }
                //normalisasi kelas distribusi
                double sum = DoubleStream.of(distribution).sum();
                if (!Double.isNaN(sum) && sum != 0) {
                    for (int i=0; i<distribution.length; ++i) {
                        distribution[i] /= sum;
                    }
                } else {
                    Exception exception = new Exception("Class distribution: NaN or sum=0");
                    throw exception;
                }
                label = maxIndex(distribution);
                classAttr = data.classAttribute();
            } else {
                // Membuat tree baru di bawah node ini
                Instances[] splitData;
                if (splitAttr.isNumeric()) {
                    splitData = splitNumericData(data, splitAttr, threshold);
                    children = new MyC45[2];
                    for (int i=0; i<2; i++) {
                        children[i] = new MyC45();
                        children[i].buildTree(splitData[i]);
                    }
                } else {
                    splitData = splitNominalData(data, splitAttr);
                    children = new MyC45[splitAttr.numValues()];
                    for (int i=0; i<splitAttr.numValues(); i++) {
                        children[i] = new MyC45();
                        children[i].buildTree(splitData[i]);
                    }
                }
            }
        }
    }
    
    // builds J48 tree classifier
    @Override
    public void buildClassifier(Instances data) throws Exception{
        //cek apakah data dapat dibuat classifier
        getCapabilities().testWithFail(data);
        
        buildTree(data);

        pruning(this,this,data);
    }
    
    //classifies a given instance using the decision tree model
    @Override
    public double classifyInstance(Instance instance){
        if (splitAttr == null) {
            return label;
        } else {
            if (splitAttr.isNumeric()){
                if (instance.value(splitAttr) <= threshold) {
                    return children[0].classifyInstance(instance);
                }
                return children[1].classifyInstance(instance);
            }
            return children[(int) instance.value(splitAttr)].classifyInstance(instance);
        }
    }
    
    // Prints the decision tree using the private toString method from below.
    @Override
    public String toString() {
        if ((distribution == null) && (children == null)) {
            return "\nMyC45: No DT model";
        }
        return "\nMyC45\n" + toString(0);
    }
    
    //Outputs a tree at a certain level
    public String toString(int level) {
        StringBuilder result = new StringBuilder();
        if (splitAttr == null) {
            if (Instance.isMissingValue(label)) {
                result.append(": null");
            } else {
                //result.append(": ").append(classAttr.value((int)label));
                result.append(": ").append(label);
            }
        } else {
            if (splitAttr.isNumeric()) {
                result.append("\n");
                int j=0;
                while(j<level) {
                    result.append("|  ");
                    j++;
                }
                result.append(splitAttr.name()).append(" <= ").append(threshold);
                result.append(children[0].toString(level+1));
                
                result.append("\n");
                j=0;
                while(j<level) {
                    result.append("|  ");
                    j++;
                }
                result.append(splitAttr.name()).append(" > ").append(threshold);
                result.append(children[1].toString(level+1));
            } else {
                for (int i=0; i<splitAttr.numValues(); i++) {
                    result.append("\n");

                    int j=0;
                    while(j<level) {
                        result.append("|  ");
                        j++;
                    }
                    result.append(splitAttr.name()).append(" = ").append(splitAttr.value(i));
                    result.append(children[i].toString(level+1));
                }
            }
        }
        return result.toString();
    }
    
    public void pruning(MyC45 root, MyC45 node, Instances test) {
        // Compare the tree before and after pruning
        // If the error afterwards is smaller, the pruned tree is used
        Double errorBeforePruning = 0.00;
        Double errorAfterPruning = 0.00;
        
        try {
            Evaluation eval = new Evaluation(test);
            try {
                // Calculating error before pruning
                eval.evaluateModel(this, test);
                errorBeforePruning = eval.errorRate();
            } catch (Exception ex) {
                Logger.getLogger(MyC45.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            if(node.splitAttr == null) { // LEAF
                // STOP
            }
            else {
                for(int i=0;i<node.children.length;i++) {
                    if(node.children[i].splitAttr != null) { // If child not leaf
                        Attribute tempSplitAttr = node.children[i].splitAttr;
                        Double tempLabel = node.children[i].label;
                        
                        // PRUNE THE TREE
                        node.children[i].splitAttr = null;
                        node.children[i].label = maxLabelOnInstances(test).intValue();
                        
                        try {
                            // Calculating error after pruning
                            eval.evaluateModel(this, test);
                            errorAfterPruning = eval.errorRate();
                        } catch (Exception ex) {
                            Logger.getLogger(MyC45.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        if(errorBeforePruning <= errorAfterPruning) { // Back to initial tree
                            node.children[i].splitAttr = tempSplitAttr;
                            node.children[i].label = tempLabel;
                        }
                        else {
                            eval.evaluateModel(this, test);
                            errorBeforePruning = eval.errorRate();
                        }
                    }
                    pruning(root, node.children[i], test);
                }  
            }
        } catch (Exception ex) {
            Logger.getLogger(MyC45.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public Double maxLabelOnInstances(Instances instances) {
        Instances newInst = new Instances(instances);
        HashMap hm = new HashMap();
        
        for(int i=0;i<newInst.numInstances();i++) {
            int init = 0;
            if(hm.get(newInst.instance(i).stringValue(newInst.numAttributes()-1)) != null) {
                init = (int)hm.get(newInst.instance(i).stringValue(newInst.numAttributes()-1));
            }
            hm.put(newInst.instance(i).value(newInst.numAttributes()-1), init+1);
        }
        return (Double) Collections.max(hm.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
}
