import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.util.*;

public class SummaryGenerator {
	private final static int MAX_LENGTH = 75;

	private final static String DEFAULT_PROPERTIES = "tokenize, ssplit, pos, lemma";
	private final static String ADD_REST_PROPERTIES = "";
	private final static String COMPRESS_PROPERTIES = "tokenize, ssplit, pos, parse";

	private static final String[] PUNCTUATION_VALUES = new String[] { "$",
			"``", "''", "(", ")", ",", "--", ".", ":" };
	private final static HashSet<String> PUNCTUATION = new HashSet<String>(
			Arrays.asList(PUNCTUATION_VALUES));
	private static final String[] CLOSED_CLASS_VALUES = new String[] { "CC",
		"CD", "IN", "DT", "RP", "PRP", "PRP$", "WP", "WP$", "MD", "CD" };
	private final static HashSet<String> CLOSED_CLASS = new HashSet<String>(
			Arrays.asList(CLOSED_CLASS_VALUES));

	private final StanfordCoreNLP pipeline, pipelineCompress;

	private final String inputDir;
	private final String outputDir;
	private final boolean posTag;

	private static int noDocs = 0;
	private static HashMap<String, Integer> df = new HashMap<String, Integer>();
	private static ArrayList<Annotation> annotations = new ArrayList<Annotation>();
	private static ArrayList<String> outNames = new ArrayList<String>();

	public SummaryGenerator(final String inputDir, final String outputDir,
			boolean posTag) {
		this.inputDir = inputDir;
		this.outputDir = outputDir;
		this.posTag = posTag;

		// creates a StanfordCoreNLP object, with POS tagging, lemmatization,
		// NER, parsing, and coreference resolution
		Properties props = new Properties();
		String properties = DEFAULT_PROPERTIES;
		if (posTag) {
			properties += ADD_REST_PROPERTIES;
		}
		props.put("annotators", properties);
		// "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		pipeline = new StanfordCoreNLP(props);

		Properties propsCompress = new Properties();
		propsCompress.put("annotators", COMPRESS_PROPERTIES);
		pipelineCompress = new StanfordCoreNLP(propsCompress);
	}

	public void scoreAndResults(boolean firstSentence) {
		// Compute TF-IDF per sentence
		for (int i = 0; i < annotations.size(); ++i) {
			BufferedWriter out = null;
			try {
				out = new BufferedWriter(new FileWriter(outNames.get(i)
						.toString()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Annotation an = annotations.get(i);
			List<CoreMap> sentences = an.get(SentencesAnnotation.class);

			String bestSentence = null;
			if (firstSentence) {
				CoreMap sentence = sentences.get(0);
				StringBuilder sentenceString = new StringBuilder();

				for (CoreLabel token : sentence.get(TokensAnnotation.class)) {			
					// this is the text of the token
					String word = token.get(TextAnnotation.class);
					
					String pos = null;
					if (posTag) {
						// this is the POS tag of the token
						pos = token.get(PartOfSpeechAnnotation.class);
					}
					if (!CLOSED_CLASS.contains(pos) && !PUNCTUATION.contains(pos)) {
						sentenceString.append(word).append(' ');
					}
				}
				
				bestSentence = sentenceString.toString();
			} else {
				// Compute term frequency scores for each word
				HashMap<String, Integer> tf = new HashMap<String, Integer>();
				for (CoreMap sentence : sentences) {
					for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
						String lemma = token.get(LemmaAnnotation.class);
						if (tf.containsKey(lemma))
							tf.put(lemma, tf.get(lemma) + 1);
						else
							tf.put(lemma, 1);
					}
				}

				// Compute sentence with best score
				double maxSentenceScore = 0;
				for (CoreMap sentence : sentences) {
					double cscore, sentenceScore = 0;
					StringBuilder sentenceString = new StringBuilder();
					for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
						String lemma = token.get(LemmaAnnotation.class);
						String word = token.get(TextAnnotation.class);

						String pos = null;
						if (posTag) {
							// this is the POS tag of the token
							pos = token.get(PartOfSpeechAnnotation.class);
						}

						if (!CLOSED_CLASS.contains(pos)
								&& !PUNCTUATION.contains(pos)) {
							sentenceString.append(word).append(' ');

							cscore = Math.log(tf.get(lemma) + 1)
									* Math.log((double) noDocs / df.get(lemma));
							sentenceScore += cscore;
						}
					}

					if (sentenceScore > maxSentenceScore) {
						maxSentenceScore = sentenceScore;
						bestSentence = sentenceString.toString();
					}
				}
			}

			try {
				// For java garbage collector
				annotations.set(i, null);
				compressSentence(bestSentence.toString());
				out.write(bestSentence.toString());
				out.close();
			} catch (IOException e) {
				System.out.println("Error at writing in file: " + bestSentence);
			}
		}
	}

	public void processAllFiles() {
		File input = new File(inputDir);
		File[] dirs = input.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});

		for (final File dir : dirs) {
			for (final File file : dir.listFiles()) {
				++noDocs;
				processSingleFile(file);

				StringBuilder outName = new StringBuilder(outputDir);
				outName.append("/");
				outName.append(dir.getName().toUpperCase()
						.substring(0, dir.getName().length() - 1));
				outName.append(".P.10.T.1.");
				outName.append(file.getName());
				outNames.add(outName.toString());
			}
		}
	}

	private void processSingleFile(final File file) {
		// read XML contents from file
		Scanner s = null;
		try {
			s = new Scanner(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		StringBuilder textBuilder = new StringBuilder();
		while (s.hasNextLine()) {
			textBuilder.append(s.nextLine());
		}
		s.close();
		// fix bug in the input files
		String text = textBuilder.toString().replaceAll("&AMP;", "&amp;");

		// read text field from XML
		try {
			nu.xom.Builder parser = new nu.xom.Builder();
			nu.xom.Document doc = parser.build(text, null);
			nu.xom.Element root = doc.getRootElement();
			text = root.getFirstChildElement("TEXT").getValue();
		} catch (nu.xom.ParsingException ex) {
			System.err
					.println("Cafe con Leche is malformed today. How embarrassing!");
		} catch (IOException ex) {
			System.err
					.println("Could not connect to Cafe con Leche. The site may be down.");
		}

		if (text == null) {
			return;
		}

		parseText(text);
	}

	// method partially inspired by an example on the Stanford Core NLP website
	// annotates and computes document frequency scores for each word
	private void parseText(final String text) {
		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		// run all Annotators on this text
		pipeline.annotate(document);

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and
		// has values with custom types
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		// indicates if a word is present in current document
		HashMap<String, Boolean> hasTerm = new HashMap<String, Boolean>();

		// process tokens to get document frequency
		for (CoreMap sentence : sentences)
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				String lemma = token.getString(LemmaAnnotation.class);
				if (!hasTerm.containsKey(lemma)) {
					hasTerm.put(lemma, true);
					if (df.containsKey(lemma))
						df.put(lemma, df.get(lemma) + 1);
					else
						df.put(lemma, 1);
				}
			}

		// Store annotation and filename in vectors at same index
		annotations.add(document);
	}

	private String compressSentence(String sentence) {
		// Remove "a" and "the" determiners
		sentence.replaceAll(" a ", " ");
		sentence.replaceAll(" A ", " ");
		sentence.replaceAll(" the ", " ");
		sentence.replaceAll(" The ", " ");
		sentence.replaceAll("^a ", " ");
		sentence.replaceAll("^A ", " ");
		sentence.replaceAll("^the ", " ");
		sentence.replaceAll("^The ", " ");

		if (sentence.length() < MAX_LENGTH)
			return sentence;

		// Remove initial adverbials
		if (sentence.startsWith("On the other hand "))
			sentence = sentence.substring(17);
		if (sentence.startsWith("For example "))
			sentence = sentence.substring(11);

		if (sentence.length() < MAX_LENGTH)
			return sentence;

		Annotation document = new Annotation(sentence);
		pipelineCompress.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		// FIXME: remove
		// Tree tree = sentences.get(0).get(TreeAnnotation.class);

		SemanticGraph dependencies = sentences.get(0).get(
				CollapsedCCProcessedDependenciesAnnotation.class);

		// Remove leaves from dependency graph
		/*
		Set<IndexedWord> leaves = dependencies.getLeafVertices();
		for (IndexedWord leaf : leaves)
			dependencies.removeVertex(leaf);

		String trimmedSentence = dependencies.toRecoveredSentenceString();
		if(trimmedSentence.length() < MAX_LENGTH) return trimmedSentence;*/
		
		// Remove temporals, abbreviations and appositives		
		List<SemanticGraphEdge> listDep = dependencies.findAllRelns(EnglishGrammaticalRelations.APPOSITIONAL_MODIFIER);
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.TEMPORAL_MODIFIER));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.ABBREVIATION_MODIFIER));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.ADJECTIVAL_MODIFIER));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.AGENT));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.ADVERBIAL_MODIFIER));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.PARTICIPIAL_MODIFIER));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.PARATAXIS));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.CONJUNCT ));
		listDep.addAll(dependencies.findAllRelns(EnglishGrammaticalRelations.COORDINATION ));
		
		for(SemanticGraphEdge edge : listDep)
		{
			IndexedWord w = edge.getDependent();
			dependencies.removeVertex(w);
		}
		// System.out.println(dependencies.toRecoveredSentenceString());
		return dependencies.toRecoveredSentenceString();
	}
}

