import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.io.IOException;
import java.io.File;
import java.util.Scanner;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.FileReader;

import java.util.Arrays;
import java.util.Set;
import java.util.Iterator;

/**
 * As a class, <code>Viterbi</code> contains both the HMM model 
 * and the functions to run the Viterbi algorithm on it. 
 * <p>
 * The class can train itself from probabilities provided by
 * a dictionary, and then save and load the needed data into a 
 * file. There are two required files for training: a corpus tagset
 * and thedirectory of the corpus. 
 * <p> 
 * The corpus tagset should be in the form of having
 * each part of speech on its own line, each POS symbol exactly 1 "word"
 * long followed by a tab and the real English english term for the POS
 * such as <br>
 * [POS symbol] \t [POS term] 
 * e.g.
 * cs	conjunction, subordinating
 * <p>
 * Each corpus text file should be a compete English text, with each word
 * tagged with a part of speech. It should conform to the format set by
 * the Brown corpus, i.e. in the form [word]/[part of speech].
 * <p>
 * The training file will be printed in the form <br>
 * numPOS <br>
 * numWords <br>
 * "word1" index_POS1 log(probability POS1) index_POS2 log(probabilty POS2) ... <br>
 * ... <br>
 * "wordlast" index_POS1 log(probability POS1) index_POS1 log(probabilty POS2) ...  <br>
 * "POS1" log(probability POS1 following) log(probability POS2 following) ... log(probability POSlast following)<br>
 * ... <br>
 * "POS2" log(probability POS1 following) log(probability POS2 following) ... log(probability POSlast following)<br>
 * where "word1", "POS1", etc are replaced by the actual words or
 * names of parts of speech
 * <p>
 * In particular, the corpus directory may contain subdirectories,
 * but should not contain non-corpus files.
 * <p>
 */
public class Viterbi
{
    /**
     * The number of words.
     */
    private int numWords;
    
    /**
     * The number of parts of speech.
     */
    private int numPOS;

    /**
     * The logs of the emission probabilities of the model, 
     * represented as a HashMap of Strings (words) to arrays
     * of probabilities.
     */
    private HashMap<String, float[]> p_emission = new HashMap<String, float[]>();

    /**
     * The logs of the transmission probabilities of the model,
     * representated as a two-dimensional array. Here,
     * transmission[POS 1][POS 2] refers to the 
     * transmission probability for moving from 
     * POS 1 to POS 2. 
     */
    private float[][] p_transmission;

    /**
     * Basic constructor
     */
    private Viterbi(String tagset, String gtagset)
    {
    }

    /**
     * Constructor; initializes the probability table from the 
     * given saved training file. (See above for format of file.)
     * @param tagset the file containing a list of parts of speech
     * @param gtagset text file containing a legend for the tags; maps a simplified
 	 * tag name to a comma-separated list of POSIndices
     * @param datafile the name of the file of saved probability data
     * @return none
     */
    public Viterbi(String tagset, String gtagset, String datafile)
    {
	// tries to load the tagset
	try
	{
	    POS.loadFromFile (tagset, gtagset);
	}
	catch (IOException e)
	{
	    System.out.println("oops. The tagset could not be loaded properly.");
	    System.exit(1);

	    // TO-DO: better error handling?
	}

	Scanner sc = null;
	// deals with the actual data file
	try 
	{
	    // tries to open the file
	    sc = new Scanner(new BufferedReader(new FileReader(datafile)));

	    // reads in the first two numerical values
	    this.numPOS = sc.nextInt();
	    sc.nextLine();
	    this.numWords = sc.nextInt();
	    sc.nextLine();

	    // basic check that the number of parts of speech matches up
	    if (this.numPOS != POS.numPOS())
		throw new Exception("The training file does not seem to match the indicated tagset.");

	    // initializes the probability array
	    this.p_transmission = new float[this.numPOS][this.numPOS];
	    
	    // reads file and loads emission probabilities
	    String word;
	    float[] probability;
	    String[] line;
	    int numEntries;
	    
	    for (int i = 0; i < numWords; i++)
	    {
		probability = new float[this.numPOS];
		word = sc.next("\\S+");
		sc.skip(" ");
		line = sc.nextLine().split(" ");
		numEntries = (line.length+1)/2;

		// sets default probabilities
		for (int j = 0; j < numPOS; j++)
		{
		    probability[j] = Float.MIN_VALUE;
		}
		
		// gets actual probabilities
		for (int j = 0; j < numEntries; j+=2)
		{
		    probability[Integer.parseInt(line[j])] = Float.parseFloat(line[j+1]);
		}

		// adds to hash map
		this.p_emission.put(word, probability);
	    }
	    
	    // reads file and loads transmission probabilities
	    for (int i = 0; i < numPOS; i++)
	    {
		// gets POS and checks that it has the right index
		word = sc.next("\\S+");
		sc.skip(" ");
		if (i != POS.getIndexBySymbol(word))
		    throw new Exception("The training file does not seem to match the indicated tagset.");

		line = sc.nextLine().split(" ");
		numEntries = (line.length+1)/2;

		// sets default probabilities
		for (int j = 0; j < numPOS; j++)
		{
		    p_transmission[i][j] = Float.MIN_VALUE;
		}
		
		// gets actual probabilities
		for (int j = 0; j < numEntries; j+=2)
		{
		    p_transmission[i][Integer.parseInt(line[j])] = Float.parseFloat(line[j+1]);
		}
	    }
	}
	catch (Exception e)
	{
	    if (e instanceof IOException)
		System.out.println("oops. The training data file could not be loaded properly.");
	    else
		System.out.println(e.getMessage());

	    System.exit(1);
	}
	finally
	{
	    if (sc != null)
		sc.close();
	}
    }

    /**
     * Iterates through the corpus and calculates the frequencies of
     * neighborings parts of speech and the frequences of each part
     * of speech for each word. 
     * @param tagset the file containing the tagset, as defined in the class 
     description.
     * @param gtagset text file containing a legend for the tags; maps a simplified
 	 * tag name to a comma-separated list of POSIndices
     * @param corpusDirectory the name of the directory containing
     the corpus
     * @param saveLocation where the probabilities are to be saved
     * @return none
     */
    public static void loadCorpusForTraining (String tagset, String gtagset,
					      String corpusDirectory,
					      String saveLocation)
    {
	int numWords = 0;

	try
    {
	   	POS.loadFromFile (tagset, gtagset);
	}
	catch (IOException e)
	{
	 	System.out.println ("File I/O Error.");
	 	System.exit(1);
	}
	
	int numPOS = POS.numPOS();
		
	/* Hashmap of number of times each word appears
	 * in the training data for each part of speech;
	 * as a hashmap of Strings to integer arrays, with each integer
	 * representing a POS index.
	 */ 
	HashMap<String, int[]> word_to_pos = new HashMap<String, int[]>();
		
	/* Two dimension of number of times each POS appears
	 * after a specific POS.
	 */
	int[][] pos_to_pos = new int[numPOS][numPOS];
	int POSIndex = -1;
	int lastPOSIndex = -1;
	
	/* One dimension array of number of times each POS appears in the corpus */
	int[] pos_frequencies = new int[numPOS];

	File dir = new File(corpusDirectory);
	File[] fl = dir.listFiles();
	    
	if (fl == null) {
	   	System.out.println ("Directory not valid.");
	   	System.exit(1);
	}

	Scanner scanner;
	    
	for (int i = 0; i < fl.length; i++)
	{
	    scanner = null;
	    	
	    try 
	    {
		    scanner = new Scanner(new BufferedReader(new FileReader(fl[i])));
        	
		    while (scanner.hasNext()) 
	    	{
			    String s = scanner.next();
            		
			    int lastIndex = s.lastIndexOf("/");
			    String word = s.substring(0, lastIndex).toLowerCase();
			    String symbol = s.substring(lastIndex + 1).
                		replaceAll(POS.getIgnoreRegex(), "");
                	
			    try 
			    {
                	POSIndex = POS.getIndexBySymbol(symbol);
			    } 
			    catch (POSNotFoundException e) 
			    {
                	System.out.println ("POS not found.");
                	System.exit(1);
			    }
                	
			    int[] arr;
                	
			    // add to word_to_pos
			    if (word_to_pos.containsKey(word))
			    {
                	word_to_pos.get(word)[POSIndex]++;
			    }
			    else 
			    {
                	arr = new int[numPOS];
                	arr[POSIndex]++;
                	word_to_pos.put (word, arr);
			    }
                	
			    // add to pos_to_pos
			    if (lastPOSIndex < 0) 
			    {
                	lastPOSIndex = POSIndex;
                	continue;
			    } else {
                	pos_to_pos[lastPOSIndex][POSIndex]++;
                	lastPOSIndex = POSIndex;
			    }
			    
			    // add to pos_frequencies
			    pos_frequencies[POSIndex]++;
			}
        } 
	    catch (Exception e)
		{
		    System.exit(1);
		}
	    finally 
        {
		    if (scanner != null)
		    {
                	scanner.close();
			}
    	}
	}
	    
	// test code
	/*
	  Set<String> ks = word_to_pos.keySet();
	  Iterator<String> iter = ks.iterator();
	  while (iter.hasNext()) {
	  String k = iter.next();
	  if (k.toLowerCase().equals("the"))
	  System.out.println (k + "\t" + Arrays.toString(word_to_pos.get(k)));
	  }
        
	  System.out.println ("Total number of words: " + word_to_pos.size());
        */
	numWords = word_to_pos.size();
	PrintWriter saveFile = null;

	try
        {
	    // open file to be saved
	    saveFile = new PrintWriter(new FileWriter(saveLocation));

	    // write numPOS
	    saveFile.println(numPOS);
	    
	    // write numWords
	    saveFile.println(numWords);
	    
	    String line;
	    
	    // write emission probabilities
	    Set<Map.Entry<String, int[]>> prob_e = word_to_pos.entrySet();
	    Iterator<Map.Entry<String, int[]>> it = prob_e.iterator();
	    Map.Entry<String, int[]> e;
	    int[] p;

	    for (int i = 0; i < numWords; i++)
	    {
		e = it.next();
		p = e.getValue();
		line = e.getKey() + " ";

		for (int j = 0; j < numPOS; j++)
		{
		    if (p[j] > 0)
			line += j + " " + Math.log((float)p[j]/pos_frequencies[j]) + " ";
		}

		saveFile.println(line);
	    }
	    
	    // write transmission probabilities
	    for (int i = 0; i < numPOS; i++)
	    {
		line = POS.getPOSbyIndex(i).getSymbol() + " ";
		for (int j = 0; j < numPOS; j++)
		{
		    if (pos_to_pos[i][j] > 0)
			line += j + " " + Math.log((float)pos_to_pos[i][j]/pos_frequencies[i]) + " ";
		}
		
		saveFile.println(line);
	    }
	}
	catch (IOException e)
	{
	    System.out.println("File could not be saved.");
	    System.exit(1);
	}
	finally
	{
	    saveFile.close();
	}
    }

    /**
     * Takes a set of outputs and determines the most likely original
     * state.
     * @param results list of outputs
     * @return list of (state, output) pairs
     */
    public ArrayList<Pair<String, POS>> parse(ArrayList<String> results)
      {
	  int length = results.size(); // number of pieces of the sentence
	  float[][] probs = new float[numPOS][length]; // probability table
	  int[][] parts = new int[numPOS][length]; // parts of speech fo the words

	  // variables for looping below
	  float value;
	  int index;
	  float[] emission;
	  String word;

	  for (int i = 0; i < length; i++) // per word
	  {
	      word = results.get(i).trim().toLowerCase();
	      if(! p_emission.containsKey(word))
	      {
		  for (int j = 0; j < numPOS; j++)
		      probs[j][i] = Float.MIN_VALUE;

		  index = POS.get_default().getIndex();
		  parts[index][i] = index;
		  probs[index][i] = 0; 
	      }
	      else
	      {
		  emission = p_emission.get(word);
		  for (int j = 0; j < numPOS; j++) // per possible POS assignment
	          {  
		      value = Float.MIN_VALUE;
		      index = 0;
		      
		      for (int k = 0; k < numPOS; k++) // per previous possible assignment
		      {
			  if (p_transmission[k][j] + emission[k] > value)
			  {
			      value = p_transmission[k][j] + emission[k];
			      index = k;
			  }
		      }
		      parts[j][i] = index;
		      probs[j][i] = value;
		  }
	      }
	  }
	  
	  int POSIndex = 0;
	  for (int i = 0; i < length; i++)
	      if (probs[numPOS-1][i] > probs[POSIndex][i])
		  POSIndex = i;

	  return create_for_parse(results, probs, parts, length-1, POSIndex);
      }

    /**
     * Helper method for parse. 
     */
    private ArrayList<Pair<String, POS>> create_for_parse(ArrayList<String> words, float[][] probs, 
							  int[][] parts, int wIndex, int pIndex)
    {
	if (wIndex < 0)
	    return new ArrayList<Pair<String,POS>>();
	
	ArrayList<Pair<String,POS>> sentence = 
	    create_for_parse(words, probs, parts, wIndex-1, parts[pIndex][wIndex]);
	sentence.add(new Pair<String, POS>(words.get(wIndex), POS.getPOSbyIndex(pIndex)));
	return sentence;
    }
}