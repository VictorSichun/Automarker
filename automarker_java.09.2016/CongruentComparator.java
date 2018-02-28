import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * @author shhn001
 * 
 */
public class CongruentComparator {
	
	public static int diffpercent = 0; // for last wrong answer

	public static StatusCodes compare(File samplefile, File solutionfile) {
		
		ArrayList<String> lineList1, lineList2;
		
		try (
				BufferedReader input1 = new BufferedReader(new FileReader(samplefile));
				BufferedReader input2 = new BufferedReader(new FileReader(solutionfile)); )
		{
			lineList1 = new ArrayList<String>();
			while (input1.ready()) {
				String line = input1.readLine();
				int m = line.length() - 1;
				while (m >= 0 && (line.charAt(m) == ' ' || line.charAt(m) == '\t'))
					m--;
				if (m > -1)
					lineList1.add(line.substring(0, m + 1));
				else
					lineList1.add(new String()); // empty line
			}
			
			lineList2 = new ArrayList<String>();
			while (input2.ready()) {
				String line = input2.readLine();
				int m = line.length() - 1;
				while (m >= 0
						&& (line.charAt(m) == ' ' || line.charAt(m) == '\t'))
					m--;
				if (m > -1)
					lineList2.add(line.substring(0, m + 1));
				else
					lineList2.add(new String()); // empty line
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			return StatusCodes.InQueue; // this will cause file to rerun later
		}
		
		// strip end blank lines
		
		int l1 = lineList1.size() - 1;
		while (l1 >= 0 && lineList1.get(l1).length() == 0) {
			lineList1.remove(l1--);
		}
		
		int l2 = lineList2.size() - 1;
		while (l2 >= 0 && lineList2.get(l2).length() == 0) {
			lineList2.remove(l2--);
		}
		
		// check if perfect match
		
		boolean correctFlag = true;
		int diffcnt = 0;
    		diffpercent = 0;  // either correct or just one line/case
		
		if (l1 == l2) {
			for (int i = 0; i <= l1; i++) {
				if (!lineList1.get(i).equals(lineList2.get(i))) {
					correctFlag = false;
					diffcnt += 1;
				}
			}

		    if (l1 > 0 && diffcnt > 0) // get diff percentage wrong
    		{
    			diffpercent = (l1-diffcnt)*100 / l1;
    		}
		} else {
			correctFlag = false;
    		diffpercent = -1; // wrong line count
		}

		
		if (correctFlag) {
			return StatusCodes.Correct;
		}
		
		// strip all spaces, empty lines, and convert to lower case
		
		for (int i = l1; i >= 0; i--) {
			String line = lineList1.get(i);
			StringTokenizer tokens = new StringTokenizer(line, " \t");
			
			StringWriter newline = new StringWriter();
			while (tokens.hasMoreTokens()) {
				newline.write(tokens.nextToken());
			}
			
			String S = newline.toString();
			if (S.length() == 0) {
				lineList1.remove(i);
				l1--;
			} else {
				lineList1.set(i, S.toLowerCase());
			}
		}
		
		for (int i = l2; i >= 0; i--) {
			String line = lineList2.get(i);
			StringTokenizer tokens = new StringTokenizer(line, " \t");
			
			StringWriter newline = new StringWriter();
			while (tokens.hasMoreTokens()) {
				newline.write(tokens.nextToken());
			}
			
			String S = newline.toString();
			if (S.length() == 0) {
				lineList2.remove(i);
				l2--;
			} else {
				lineList2.set(i, S.toLowerCase());
			}
		}
		
		correctFlag = true;
		
		for (int i = 0; i <= l1 && i <= l2; i++) {
			if (!lineList1.get(i).equals(lineList2.get(i))) {
				correctFlag = false;
			}
		}
		
		if (l1 == l2 && correctFlag == true) {
			return StatusCodes.PresentationError;
		}
		
		return StatusCodes.WrongAnswer;
	}
	
}
