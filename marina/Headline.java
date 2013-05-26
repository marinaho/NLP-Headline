import java.io.*;
import java.util.*;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.trees.TreeCoreAnnotations.*;


public class Headline {
	
	static final String inputDir = "./docs";

	static int noDocs = 0;
	static StanfordCoreNLP pipeline = null;
	static HashMap<String, Integer> df = new HashMap<String, Integer>();
	static ArrayList<Annotation> annotations = new ArrayList<Annotation>();
	static ArrayList<String> filenames = new ArrayList<String>();

	static CoreMap compressSentence(CoreMap s)
	{
		// TO DO:
	    return s;
	}
	static void annotate(String dirname, HashMap<String, Integer> df, ArrayList<Annotation> annotations, ArrayList<String> filenames)
	{
		File dirparent = new File(dirname);
		String text = "", line;
		for (File dir : dirparent.listFiles()) 
			if(dir.isDirectory())
			{				
				for (File file: dir.listFiles()) {
					HashMap<String, Boolean> hasTerm = new HashMap<String, Boolean>();
					BufferedReader br = null;
					++noDocs;
					try {
						br = new BufferedReader(new FileReader(file)); 
						for(text = ""; (line = br.readLine()) != null;)
							if(line.startsWith("<DOC>") == false && line.startsWith("<DOCNO>") == false && line.startsWith("<DOCTYPE>") == false &&
							line.startsWith("<TXTTYPE>") == false && line.startsWith("<TEXT>") == false && line.startsWith("</TEXT>") == false &&
							line.startsWith("</DOC>") == false)
								text += line;
					}
					catch(FileNotFoundException e) {
						System.out.println("Couldn't find file " + file.getName());
						e.printStackTrace();
					}
					catch(IOException e) {
						System.out.println("Error at reading from file " + file.getName());
						e.printStackTrace();
					}
					finally {
						if(br != null)
							try {
								br.close();
							} catch (IOException e) {
								System.out.println("Error at closing reader of file " + file.getName());
								e.printStackTrace();
							}
					}

					// create an empty Annotation just with the given text
					Annotation document = new Annotation(text);

					// run all Annotators on this text
					System.out.println("Annotating " + dir.getName() + " " + file.getName());
					pipeline.annotate(document);
					System.out.println("done");

					// these are all the sentences in this document
					// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
					List<CoreMap> sentences = document.get(SentencesAnnotation.class);

					for(CoreMap sentence: sentences)
						for(CoreLabel token: sentence.get(TokensAnnotation.class))
						{
							String word = token.getString(TextAnnotation.class);
							if(!hasTerm.containsKey(word))
							{
								hasTerm.put(word, true);
								if(df.containsKey(word))
									df.put(word, df.get(word) + 1);
								else df.put(word, 1);
							}
						}
					annotations.add(document);
					filenames.add("./results/" + dir.getName().substring(0, dir.getName().length()-1).toUpperCase() + ".P.10.T.1." + file.getName());
				}
			}
	}
	static StanfordCoreNLP initializePipeline(String init)
	{
		Properties props = new Properties();
		props.put("annotators", init);
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		return pipeline;
	}
	static void processDocs()
	{
		File resultDir = new File("./results/");
		if (!resultDir.exists()) resultDir.mkdir();
		
		// Compute TF-IDF per sentence
		for(int i = 0; i < annotations.size(); ++i)
		{
			System.out.println("Scoring document " + filenames.get(i));
			File file = new File(filenames.get(i));
			if(!file.exists())
			{
			  try {
			      file.createNewFile();
			  }
			  catch(IOException ioe) {
				  System.out.println("Couldn't create file " + filenames.get(i));
			      ioe.printStackTrace();
			  }
			}
			PrintWriter writer = null;
			try {
				writer = new PrintWriter(filenames.get(i));
			} catch (FileNotFoundException e) {
				System.out.println("Could not find file " + filenames.get(i));
				e.printStackTrace();
				return;
			}
			Annotation an = annotations.get(i);
			List<CoreMap> sentences = an.get(SentencesAnnotation.class);
			HashMap<String, Integer> tf = new HashMap<String, Integer>();
			for(CoreMap sentence: sentences)
			{
				for(CoreLabel token: sentence.get(TokensAnnotation.class))
				{
					String word = token.get(TextAnnotation.class);
					if(tf.containsKey(word))
						tf.put(word, tf.get(word) + 1);
					else tf.put(word, 1);
				}
			}
			double maxSentenceScore = 0;
			CoreMap bestSentence = null;
			for(CoreMap sentence : sentences)
			{
				double scoren1 = 0, scoren2 = 0, scorev1 = 0, scorev2 = 0, cscore, sentenceScore = 0;
				String wordn1 = "", wordn2 = "", wordvb1 = "", wordvb2 = "";
				for(CoreLabel token: sentence.get(TokensAnnotation.class))
				{
					String word = token.get(TextAnnotation.class);
					String pos = token.get(PartOfSpeechAnnotation.class);

					cscore = Math.log(tf.get(word) + 1) * Math.log((double)noDocs / df.get(word));
					sentenceScore += cscore;
					if(sentenceScore > maxSentenceScore)
						bestSentence = sentence;
					/* Find 2 best nouns, 2 best verbs
					 * if(pos.indexOf("NN") == 0)
					{
						if(cscore > scoren1) {
							scoren2 = scoren1;
							wordn2 = wordn1;
							scoren1 = cscore;
							wordn1 = word;
						}else if(cscore > scoren2 && (word.compareTo(wordn1) != 0)) {
							scoren2 = cscore;
							wordn2 = word;
						} 

					}
					else if(pos.indexOf("VB") == 0) {
						if(cscore > scorev1) {
						   scorev2 = scorev1;
						   wordvb2 = wordvb1;
						   scorev1 = cscore;
						   wordvb1 = word;
						}else if(cscore > scorev2 && (word.compareTo(wordvb1) != 0)) {
						   scorev2 = cscore;
						   wordvb2 = word;
						}
					}*/
				}

			    // this is the parse tree of the current sentence; needs parse in main in the pipeline init
			    // System.out.println("Sentence:"+sentence);
			    // Tree tree = sentence.get(TreeAnnotation.class); needs parse in main in the pipeline init

			    // this is the Stanford dependency graph of the current sentence
			    // SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
			    
			 //   System.out.println("Tree:" + tree);
			 //   System.out.println("Dependencies:" + dependencies);
			}
			System.out.println("Best sentence: " + bestSentence);
			writer.println(bestSentence);
			if(writer != null) writer.close();
			//System.out.println("Most important nouns " + wordn1 + " " + wordn2 + " scores " + scoren1 + " " + scoren2);
			//System.out.println("Most important verbs " + wordvb1 + " " + wordvb2 + " scores " + scorev1 + " " + scorev2);
		}
	}
	public static void main(String[] args)
	{	
		// TO DO: add pos, lemma, ner, parse, dcoref or other processing
		pipeline = initializePipeline("tokenize, ssplit, pos");
		annotate(inputDir, df, annotations, filenames);
		processDocs();
	}
}
