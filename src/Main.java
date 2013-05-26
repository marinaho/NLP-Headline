
public class Main {
	private final static String defaultInput = "docs";
	private final static String defaultOutput = "./eval/peers/1";
	
	public static void main(String[] args) {
		SummaryGenerator sg = new SummaryGenerator(defaultInput, defaultOutput, true);
		sg.processAllFiles();
		sg.scoreAndResults();
	}
}
