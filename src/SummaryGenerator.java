import java.io.*;
import java.util.*;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.TreeCoreAnnotations.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.util.*;

public class SummaryGenerator {
	private final static int MAX_LENGTH = 75;
	
	private final static String DEFAULT_PROPERTIES = "tokenize, ssplit";
	private final static String ADD_REST_PROPERTIES = ", pos";
	
	private static final String[] PUNCTUATION_VALUES = new String[] {"$", "``", "''", "(", ")", ",", "--", ".", ":"};
	private final static HashSet<String> PUNCTUATION = new HashSet<String>(Arrays.asList(PUNCTUATION_VALUES));
	private static final String[] CLOSED_CLASS_VALUES = new String[] {"CC", "CD", "IN", "DT", "RP", "PRP", "PRP$", "WP", "WP$", "MD", "CD"};
	private final static HashSet<String> CLOSED_CLASS = new HashSet<String>(Arrays.asList(CLOSED_CLASS_VALUES));
	
	private final StanfordCoreNLP pipeline;
	
	private final String inputDir;
	private final String outputDir;
	private final boolean posTag;

	public SummaryGenerator(final String inputDir, final String outputDir, boolean posTag) {
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
				String firstSentence = processSingleFile(file);

				BufferedWriter out = null;
				StringBuilder outName = new StringBuilder(outputDir);
				outName.append("/");
				outName.append(dir.getName().toUpperCase()
						.substring(0, dir.getName().length() - 1));
				outName.append(".P.10.T.1.");
				outName.append(file.getName());
				try {
					out = new BufferedWriter(new FileWriter(outName.toString()));
					out.write(firstSentence.substring(0, Math.min(firstSentence.length(), MAX_LENGTH)));
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private String processSingleFile(final File file) {
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
			return null;
		}

		return parseText(text);
	}

	// method partially inspired by an example on the Stanford Core NLP website
	private String parseText(final String text) {
		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		// run all Annotators on this text
		pipeline.annotate(document);

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and
		// has values with custom types
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		// return first sentence
		CoreMap sentence = sentences.get(0);
		StringBuilder firstSentence = new StringBuilder();
		// traversing the words in the current sentence
		// a CoreLabel is a CoreMap with additional token-specific methods
		for (CoreLabel token : sentence.get(TokensAnnotation.class)) {			
			// this is the text of the token
			String word = token.get(TextAnnotation.class);
			
			String pos = null;
			if (posTag) {
				// this is the POS tag of the token
				pos = token.get(PartOfSpeechAnnotation.class);
			}
			if (!CLOSED_CLASS.contains(pos) && !PUNCTUATION.contains(pos)) {
				firstSentence.append(word).append(' ');
			}
		}

		return firstSentence.toString();

		// TODO: remove
		// for (CoreMap sentence : sentences) {
		// // traversing the words in the current sentence
		// // a CoreLabel is a CoreMap with additional token-specific methods
		// for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
		// // this is the text of the token
		// String word = token.get(TextAnnotation.class);
		// // this is the POS tag of the token
		// String pos = token.get(PartOfSpeechAnnotation.class);
		// // this is the NER label of the token
		// String ne = token.get(NamedEntityTagAnnotation.class);
		// }
		//
		// // this is the parse tree of the current sentence
		// Tree tree = sentence.get(TreeAnnotation.class);
		//
		// // this is the Stanford dependency graph of the current sentence
		// SemanticGraph dependencies = sentence
		// .get(CollapsedCCProcessedDependenciesAnnotation.class);
		// }
		//
		// // This is the coreference link graph
		// // Each chain stores a set of mentions that link to each other,
		// // along with a method for getting the most representative mention
		// // Both sentence and token offsets start at 1!
		// Map<Integer, CorefChain> graph = document
		// .get(CorefChainAnnotation.class);
	}
}
