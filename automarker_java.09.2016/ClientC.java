import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientC
{
	public static void main(String[] args)
	{
		if (args.length == 0)
		{
			displayHelp();
			System.exit(1);
		}
		
		String command = args[0];
		
		try
		{
			ServerInterface server = (ServerInterface) Naming
					.lookup("//automarker.cs.auckland.ac.nz:8081/am_server");
			
			if (command.equals("test"))
			{
				// this command is for checking if Naming.lookup() above succeeded
				// as long as no exceptions are thrown then the test has passed
				System.out.println("online");
			}
			else if (command.equals("start") || command.equals("stop") || command.equals("reset"))
			{
				// get assignment number
				int id = parseInt(args[1], -1);
				if (id < 0)
				{
					id = server.getAssignmentId(args[1]);
				}
				
				if (id < 0)
				{
					System.out.println("Unknown assignment name.");
					System.exit(3);
				}
				
				if (command.equals("start"))
				{
					boolean started = server.startMarker(id);
					System.out.println(started);
				}
				else if (command.equals("stop"))
				{
					boolean stopped = server.stopMarker(id);
					System.out.println(stopped);
				}
				else if (command.equals("reset"))
				{
					boolean reset = server.resetMarker(id);
					System.out.println(reset);
				}
			}
			else if (command.equals("get-wait"))
			{
				int interval = server.getWaitingInterval();
				System.out.println(interval);
			}
			else if (command.equals("set-wait"))
			{
				int interval = parseInt(args[1], -1);
				int result = server.setWaitingInterval(interval);
				System.out.println(result);
			}
			else if (command.equals("get-running"))
			{
				String[] names = server.getRunningAssignments();
				// System.out.println("" + names.length);
				for (String n : names)
				{
					System.out.println(n);
				}
			}
			else if (command.equals("get-lastrun"))
			{
				Date lastRun = server.getLastRunTime();
				System.out.println(lastRun == null ? "null" : new SimpleDateFormat(
						"yyyy-MM-dd HH:mm:ss").format(lastRun));
			}
			else if (command.equals("run-now"))
			{
				server.forceRun();
				System.out.println("true");
			}
			else
			{
				System.out.println("Unknown command.");
			}
		}
		catch (NotBoundException e)
		{
			System.out.println("offline");
			System.exit(1);
		}
		catch (Exception e)
		{
			e.printStackTrace(System.out);
			System.exit(2);
		}
	}
	
	public static int parseInt(String string, int other)
	{
		try
		{
			return Integer.parseInt(string);
		}
		catch (NumberFormatException e)
		{
			return other;
		}
	}
	
	public static void displayHelp()
	{
		String[] lines = {
				"Usage:\njava ClientC <command> [args]",
				"start <name>|<id>, stop <name>|<id>, reset <name>|<id>",
				"\tStarts, stops or resets marking the specified assignment.",
				"\t'name' is formatted '<year>.<semester>.<name>.<assignment-number>'.",
				"get-running",
				"\tGets the names of the assignments the marker is currently marking.",
				"run-now",
				"\tForces the marker to immediately check for submissions and mark them.",
				"get-wait, set-wait <seconds>",
				"\tGets or sets the waiting interval between marking submissions.",
				"get-lastrun",
				"\tGets the date and time of the most recent run.",
		};
		
		for (String line : lines)
		{
			System.out.println(line);
		}
	}
}
