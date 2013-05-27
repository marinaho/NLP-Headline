
public class Main {
	private final static String defaultInput = "temp";
	private final static String defaultOutput = "./eval/peers/1";
	
	private final static boolean REMOVE_CLOSED_CLASS = false;
	private final static boolean FIRST_SENTENCE_ONLY = false;
	private final static boolean COMPRESS_SENTENCE = true;
	
	public static void main(String[] args) {
		SummaryGenerator sg = new SummaryGenerator(defaultInput, defaultOutput, REMOVE_CLOSED_CLASS);
		sg.processAllFiles();
		sg.scoreAndResults(FIRST_SENTENCE_ONLY, COMPRESS_SENTENCE);
	}
}
