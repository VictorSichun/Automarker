import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author shhn001
 * Modified by grob083
 * 
 */
public class SubmissionRunner implements AutoCloseable, Runnable
{
	private final File outputfile;
	
	private final File inputfile;
	
	private final String command;
	
	/** this is used for killing the subprocess */
	private final String rawCommand;
	
	private Process proc;
	
	private int exitValue = -1;
	
	private String error = "";
	
	/** Runs a single submission and collects the results. */
	public SubmissionRunner(File outputfile, File inputfile, String command)
	{
		this.outputfile = outputfile;
		this.inputfile = inputfile;
		this.command = "/usr/bin/time -f %U " + command; // take "time" along for the ride
		// using %U instead of %e for ... reasons
		rawCommand = command;
	}
	
	// handles execution of the process
	@Override
	public void run()
	{
		try
		{
			// delete output file if it exists
			if (outputfile.isFile())
			{
				outputfile.delete();
			}
		}
		catch (SecurityException e)
		{
			// permission error? shouldn't happen
			e.printStackTrace();
			exitValue = -999; // big problem
			return;
		}
		startProcess();
		redirectError(); // blocking
		try
		{
			exitValue = proc.waitFor(); // blocking
		}
		catch (InterruptedException ignoredException) {}
		System.out.println("process finished with exitValue=" + exitValue);
	}
	
	/** runs the submission (command) */
	private boolean startProcess()
	{
		ProcessBuilder pb = new ProcessBuilder(command.split(" "));
		System.out.println("runner: redirecting" + inputfile.getName() +inputfile.getPath());
		pb.redirectInput(inputfile);
		pb.redirectOutput(outputfile);
		try
		{
			proc = pb.start();
		}
		catch (IOException e)
		{
			System.out.println("Failed to start. Assume broken redirection pipe");
			e.printStackTrace();
			error += "IOException (startProcess)\n";
			return false;
		}
		return true;
	}
	
	/** Blocks while reading error stream into error field */
	private void redirectError()
	{
		System.out.print("Starting redirecting error stream... ");
		try (
				InputStreamReader isr = new InputStreamReader(proc.getErrorStream());
				BufferedReader stdErr = new BufferedReader(isr); )
		{
			
			String line;
			while ((line = stdErr.readLine()) != null)
			{
				error += line + "\n";
			}
		}
		catch (Exception e)
		{
			System.out.println("Exception caught while redirecting error stream");
			e.printStackTrace();
		}
		System.out.println("Finished redirecting error stream");
	}
	
	/** Gets the submission's error output. Should call {@link #close()} first. */
	public String getError()
	{
		String trimmed = error.trim();
		System.out.println("Getting error. Trimmed error string is:\n" + trimmed);
		// last line is output from "time" so we need to remove it
		int index = trimmed.lastIndexOf('\n') + 1;
		return trimmed.substring(0, index).trim();
	}
	
	/** Gets the submission's execution time. Should call {@link #close()} first. */
	public float getExecTime()
	{
		String trimmed = error.trim();
		// output from "time" is on the last line
		int index = trimmed.lastIndexOf('\n') + 1;
		trimmed = trimmed.substring(index).trim();
		
		index = 0;
		while (index < trimmed.length() && !Character.isDigit(trimmed.charAt(index)))
			index++;
		trimmed = trimmed.substring(index);
		
		if (trimmed.length() == 0)
		{
			System.out.println("no exec time found!");
			return -1;
		}
		
		try
		{
			return Float.parseFloat(trimmed.substring(index).trim());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
	
	/** Gets the submission's exit value. Should call {@link #close()} first. */
	public int getExitValue()
	{
		return exitValue;
	}
	
	/**
	 * Kills the running submission. Should ensure this thread will finish running soon. Note that
	 * {@link #getError()} {@link #getExecTime()} and {@link #getExitValue()} may return final
	 * results only after this thread has finished.
	 */
	@Override
	public void close()
	{
		try
		{
			Runtime.getRuntime().exec(new String[] { "pkill", "-f", rawCommand });
		}
		catch (IOException e)
		{
			System.out.println("Exception caught while killing subprocess");
			e.printStackTrace();
		}
		// proc.destroy(); // this is unnecessary because "pkill" already kills it
	}
	
}
