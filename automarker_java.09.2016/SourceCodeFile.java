import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @author shhn001
 * 
 */
public class SourceCodeFile {
	public static final int COMPILE = 0;
	public static final int OUTPUT = 1; // Note, not used any more
	public static final int EXTENSION = 2;
	public static final int RUN = 3;
	public static final int TIME_FACTOR = 4; // makes different programming languages have a more proper time limit.
	
	private String error = "";
	private float executionTime = -1;
	
	private static String[][] languageOptions = null;
	private static long lastModified = 0;
	public static final String xmlFile = "compilers.xml";
	
	private File submission;
	private String submissionFullName;
	private String problemName;
	private int programmingLanguage;
	
	/**
	 * @param filename A path (relative or absolute) to the source file.
	 * @param problem The name of the problem. This is used for renaming the source file (e.g. Java
	 *        requires the filename matches the class name).
	 */
	public SourceCodeFile(String filename, String problem) {
		
		loadXml();
		
		programmingLanguage = evalProgrammingLanguage(filename);
		
		submission = new File(filename);
		submissionFullName = submission.getName();
		problemName = problem;
	}
	
	public void loadXml() {
		try {
			File f = new File(xmlFile);
			if (lastModified < f.lastModified()) {
				lastModified = f.lastModified();
				
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setIgnoringComments(true);
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document xmlDoc = builder.parse(f);
				xmlDoc.getDocumentElement().normalize();
				NodeList compilerStuff = xmlDoc.getElementsByTagName("Compilers");
				
				Element e = (Element) compilerStuff.item(0);
				compilerStuff = e.getElementsByTagName("*");
				
				languageOptions = new String[(compilerStuff.getLength()) / 6][5];
				int count = 0;
				for (int i = 0; i < compilerStuff.getLength(); i = i + 6) {
					e = (Element) compilerStuff.item(i);
					
					NodeList nodes = e.getElementsByTagName("*");
					
					languageOptions[count][0] = nodes.item(0).getTextContent();
					languageOptions[count][1] = nodes.item(1).getTextContent();
					languageOptions[count][2] = nodes.item(2).getTextContent();
					languageOptions[count][3] = nodes.item(3).getTextContent();
					languageOptions[count][4] = nodes.item(4).getTextContent();
					count++;
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public int getProgrammingLanguage() {
		return programmingLanguage;
	}
	
	public int compile() {
		return compile(true, 0);
	}

	public int compile(boolean firstTry) { return compile(firstTry, 0);}

	public int compile(int verifier) { return compile(true, verifier);}
	private int compile(boolean firstTry, int verifier) {
		error = "";
		if (programmingLanguage == -1)
		{
			return -1;
		}
		
		// Clean up files in submission dir
		// do this at the start so there's time to manually check the submissions if needed
		File parentDir = submission.getParentFile();
		if (parentDir.exists() && parentDir.isDirectory() && verifier == 0)
		{
			for (String childName : parentDir.list())
			{
				if (!childName.equals(submission.getName()))
				{
					File fdel = new File(parentDir, childName);
					fdel.delete();
				}
			}
		}

		String inputExt = languageOptions[programmingLanguage][EXTENSION];
		File renamedSubmission = new File(parentDir, problemName + inputExt);
		boolean renameSuccessful = submission.renameTo(renamedSubmission);
		submission = renamedSubmission; // renameTo doesn't update the file object
		if (!renameSuccessful)
		{
			return -2;
		}
		
		Thread currentThread = Thread.currentThread();
		int priority = currentThread.getPriority();
		currentThread.setPriority(Thread.MAX_PRIORITY);
		
		String compileCmd = languageOptions[programmingLanguage][COMPILE];
		compileCmd = compileCmd.replace("basename.parent", submission.getParent().replace("\\", "/"));
		compileCmd = compileCmd.replace("basename", submission.getPath().replace("\\", "/"));
		compileCmd = compileCmd.replace("absolutepath",
				submission.getParentFile().getAbsolutePath().replace("\\", "/"));
		compileCmd = compileCmd.replace("problem", problemName);
		System.out.println("compile [" + submissionFullName + "]: " + compileCmd);
		
		int termination = -1;
		try {
			Process process = Runtime.getRuntime().exec(compileCmd);
			
			// hack?
			Thread.sleep(1000);
			getOutputStream(process);
			process.waitFor();
			
			termination = process.exitValue();
			if(termination == 1){
				System.out.println(getError());
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		System.out.println("compile [" + submissionFullName + "]: result " + termination);
		currentThread.setPriority(priority);
		
		// If compile failed, attempt to change Java file name to match main class name
		if (firstTry && termination != 0 && inputExt.equals(".java"))
		{
			Matcher mainClassMatcher = Pattern.compile(
					".* is public, should be declared in a file named (.*?)\\.java.*",
					Pattern.DOTALL).matcher(error);
			if (mainClassMatcher.matches())
			{
				String correctName = mainClassMatcher.group(1);
				if (correctName != null && correctName.length() > 0)
				{
					problemName = correctName;
					System.out.println("compile: retrying with name: " + correctName);
					return compile(false);
				}
			}
		}
		return termination;
	}
	
	public void getOutputStream(Process p) {
		String s = null;
		BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
		BufferedReader stdErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		
		try {
			while (true) {
				if (stdOut.ready()) {
					s = stdOut.readLine();
					
					if (s == null) {
						break;
					}
					
					error += s + "\n";
				}
				else if (stdErr.ready()) {
					s = stdErr.readLine();
					
					if (s == null) {
						break;
					}
					
					error += s + "\n";
				} else {
					break;
				}
				
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int execute(File outputfile, File inputfile, int timeLimit)
	{
		Thread mainThread = Thread.currentThread();
		int priority = mainThread.getPriority();
		
		int exitValue = -2;
		float newTimeLimit = timeLimit * Float.parseFloat(languageOptions[programmingLanguage][TIME_FACTOR]); // more variable time limit for different programming languages
		error = "";
		String runCmd = languageOptions[programmingLanguage][RUN];
		runCmd = runCmd.replace("basename.parent", submission.getParent().replace("\\", "/"));
		runCmd = runCmd.replace("basename", submission.getPath().replace("\\", "/"));
		runCmd = runCmd.replace("absolutepath",
				submission.getParentFile().getAbsolutePath().replace("\\", "/"));
		runCmd = runCmd.replace("problem", problemName);
		
		System.out.println("execute [" + submissionFullName + "]: " + runCmd);
		
		try (SubmissionRunner sr = new SubmissionRunner(outputfile, inputfile, runCmd); )
		{
			Thread srThread = new Thread(sr);
			mainThread.setPriority(Thread.MAX_PRIORITY);
			srThread.start();
			
			// wait for the program till it finishes or till the time limit is exceeded
			// give some extra time for system overhead, checked later
			srThread.join((int) ((1.2*newTimeLimit + 5) * 1000));
			 
			// Close the process and give a little time to ensure it can finish
			boolean overTimeLimit = srThread.isAlive();
			sr.close();
			srThread.join(10 * 1000);
			if (srThread.isAlive()) {
				System.out.println("Submission Thread is refusing to die. Memory leak!");
			}
			
			// Will only be set if the process finished of its own accord
			exitValue = sr.getExitValue(); // will be 143 (SIGTERM) if process was killed
			error = sr.getError();
			if (submission.getName().endsWith(".py") || 
		  	    submission.getName().endsWith(".py3") || 
				submission.getName().endsWith(".py2"))
			{
				error = reverseLineOrderExcludingTime(error); // Python traces are in reverse
			}
			executionTime = sr.getExecTime();
			System.out.println("execute exitValue=" + exitValue + " executionTime="
					+ executionTime + " overTimeLimit=" + overTimeLimit);
			
			// thread is given extra time, so check for actual execution time
			if (executionTime > newTimeLimit || overTimeLimit)
			{
				exitValue = -2; // out of time
			}
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		mainThread.setPriority(priority);
		
		System.out.println("execute [" + submissionFullName + "]: result " + exitValue);
		
		return exitValue;
	}
	
	public String getError() {
		String tmp = error;
		error = "";
		return tmp;
	}
	
	public float getExecTime() {
		return executionTime;
	}
	
	// finds the programming language used by looking at the extension
	protected int evalProgrammingLanguage(String filename) {
		int extPos = filename.lastIndexOf('.');
		if (extPos == -1)
		{
			return -1;
		}
		
		String extension = filename.substring(extPos).toLowerCase();
		for (int i = 0; i < languageOptions.length; i++)
		{
			if (languageOptions[i][EXTENSION].equals(extension))
			{
				return i;
			}
		}
		
		return -1;
	}
	
	private static String reverseLineOrderExcludingTime(String s) {
		String[] split = s.split("\r?\n");
		StringBuilder result = new StringBuilder();
		boolean skippedTimeOutput = false;
		for (int i = split.length - 1; i >= 0; i--)
		{
			// exclude last line if it is the output from the time command
			if (i == split.length - 1 && split[i].contains("Command exited with non-zero status"))
				skippedTimeOutput = true;
			else
				result.append(split[i]).append("\n");
		}
		if (skippedTimeOutput)
		{
			result.append(split[split.length - 1]).append("\n");
		}
		return result.toString().trim();
	}
	
}
