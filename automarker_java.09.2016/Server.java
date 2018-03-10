import java.io.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server implements ServerInterface
{
	private static final int DEFAULT_WAITING_INTERVAL = 300;
	
	// looping thread stuff
	private Thread retrieveThread = null;
	private boolean doRetrieveLoop;
	
	private int waitingInterval = DEFAULT_WAITING_INTERVAL; // in seconds
	private java.util.Date lastRunTime = null;
	
	private Vector<Integer> startedAssignments;
	private Vector<Integer> stoppedAssignments;
	private Queue<Integer> submissionBuffer;
	
	private List<Thread> workers;
	
	public static void main(String[] args)
	{
		try
		{
			new Server();
		}
		catch (Exception e) // catch all exceptions
		{
			System.out.println("Fatal exception!");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public Server()
	{
		// initialise some stuff
		startedAssignments = new Vector<Integer>();
		stoppedAssignments = new Vector<Integer>();
		submissionBuffer = new ConcurrentLinkedQueue<Integer>();
		workers = new ArrayList<Thread>();
		
		// rmi stuff
		try
		{
			LocateRegistry.createRegistry(8081);
			Registry reg = LocateRegistry.getRegistry(8081);
			ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(this, 0);
			reg.rebind("am_server", stub);
		}
		catch (RemoteException e)
		{
			System.out.println("Fatal exception: failed to bind to registry");
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("main: Server is bound to registry.");
		
		// create worker threads
		// problems are usually CPU-bound so don't create more threads than the CPU can handle
		int numberOfThreads = 1;// Runtime.getRuntime().availableProcessors();
		for (int i = 0; i < numberOfThreads; i++)
		{
			Thread t = new Thread(new SubmissionWorker(submissionBuffer));
			workers.add(t);
			t.start();
		}
		
		System.out.println("main: Created " + numberOfThreads + " worker thread(s).");
		
		// start retrieving submissions at regular intervals
		startRetrieveThread();
	}
	
	public synchronized void startRetrieveThread()
	{
		if (retrieveThread == null || !retrieveThread.isAlive())
		{
			doRetrieveLoop = true;
			retrieveThread = new Thread(new RetrieveThread());
			retrieveThread.start();
			
			System.out.println("main: Started retrieval thread");
		}
	}
	
	public synchronized void stopRetrieveThread()
	{
		if (retrieveThread != null && retrieveThread.isAlive())
		{
			doRetrieveLoop = false;
			retrieveThread.interrupt();
			
			// wait for it to stop
			try
			{
				retrieveThread.join();
			}
			catch (InterruptedException e)
			{
				// just stop waiting
			}
			
			System.out.println("main: Stopped retrieval thread");
		}
	}
	
	// implementation of ServerInterface methods
	
	@Override
	public int getAssignmentId(String name)
	{
		if (name.equals(""))
			return -1;
		
		// get course name and assignment number from name
		String course = "";
		int number = -1;
		try
		{
			int index = name.lastIndexOf('.');
			course = name.substring(0, index);
			number = Integer.parseInt(name.substring(index + 1));
		}
		catch (NumberFormatException e)
		{
			// throw new IllegalArgumentException("Invalid name.");
			return -1;
		}
		try (
				Connection conn = DbConnection.getConnection();
				PreparedStatement sql = conn.prepareStatement(
						"select assignment_id " +
						"from am_assignments join am_courses using (course_id) " +
						"where course_fullname = ? and assignment_number = ?"); )
		{
			sql.setString(1, course);
			sql.setInt(2, number);
			
			ResultSet rs = sql.executeQuery();
			if (rs.next()) // there will only be a maximum of 1 result
			{
				return rs.getInt("assignment_id");
			}
			else
			{
				return -1;
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			return -1;
		}
	}
	
	@Override
	public String[] getRunningAssignments()
	{
		try (
				Connection conn = DbConnection.getConnection();
				PreparedStatement sql = conn.prepareStatement(
						"select assignment_id, " +
						"concat(course_fullname, '.', assignment_number) as name " +
						"from am_assignments join am_courses using (course_id) " +
						"where (start_time <= now() and end_time > now()) " +
						buildStartedAssignmentString()); )
		{
			List<String> results = new ArrayList<String>();
			ResultSet rs = sql.executeQuery();
			while (rs.next())
			{
				int aid = rs.getInt("assignment_id");
				String name = rs.getString("name");
				
				if (!stoppedAssignments.contains(aid))
				{
					if (startedAssignments.contains(aid))
					{
						name += "*";
					}
					
					results.add(aid + " " + name);
				}
			}
			
			return results.toArray(new String[0]);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			return new String[0];
		}
	}
	
	@Override
	public synchronized boolean startMarker(int id)
	{
		if (isValidAssignmentId(id))
		{
			// must cast or java thinks you want to remove based on index
			if (stoppedAssignments.contains(id))
			{
				stoppedAssignments.remove((Integer) id);
			}
			
			// only add to manually started list if it won't be automatically checked
			Settings s = Settings.getSettings(id);
			java.util.Date now = new java.util.Date();
			if (!startedAssignments.contains(id)
					&& (now.before(s.getStartTime()) || now.after(s.getEndTime())))
			{
				startedAssignments.add(id);
			}
			
			System.out.println("main: Started assignment " + id);
			return true;
		}
		
		return false;
	}
	
	@Override
	public synchronized boolean stopMarker(int id)
	{
		if (isValidAssignmentId(id))
		{
			// must cast or java thinks you want to remove based on index
			if (startedAssignments.contains(id))
			{
				startedAssignments.remove((Integer) id);
			}
			
			// only add to manually stopped list if it will be automatically checked
			Settings s = Settings.getSettings(id);
			java.util.Date now = new java.util.Date();
			if (!stoppedAssignments.contains(id)
					&& (now.after(s.getStartTime()) && now.before(s.getEndTime())))
			{
				stoppedAssignments.add(id);
			}
			
			System.out.println("main: Stopped assignment " + id);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean resetMarker(int id)
	{
		// update rows in database to signal remark
		try (
				Connection conn = DbConnection.getConnection();
				PreparedStatement sql = conn.prepareStatement(
						"update am_submissions set status_code = ? " +
						"where assignment_id = ?"); )
		{
			sql.setInt(1, StatusCodes.InQueue.ordinal());
			sql.setInt(2, id);
			sql.executeUpdate();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			return false;
		}
		
		// force rerun to remark reset submissions
		System.out.println("main: Reset submission status codes for assignment " + id);
		forceRun();
		return true;
	}
	
	@Override
	public void forceRun()
	{
		stopRetrieveThread();
		startRetrieveThread();
	}
	
	@Override
	public int setWaitingInterval(int seconds)
	{
		if (seconds > 0)
		{
			waitingInterval = seconds;
			forceRun(); // interrupt the thread if it is sleeping
		}
		
		return waitingInterval;
	}
	
	@Override
	public int getWaitingInterval()
	{
		return waitingInterval;
	}
	
	@Override
	public java.util.Date getLastRunTime()
	{
		return lastRunTime;
	}
	
	// helper methods
	
	private static boolean isValidAssignmentId(int id)
	{
		try
		{
			Settings.getSettings(id);
			return true;
		}
		catch (IllegalArgumentException e)
		{
			return false;
		}
	}
	
	private String buildStartedAssignmentString()
	{
		if (startedAssignments.size() == 0)
		{
			return "";
		}
		
		String sep = "";
		StringBuilder sb = new StringBuilder();
		sb.append(" or assignment_id in (");
		for (Integer i : startedAssignments)
		{
			sb.append(sep + i);
			sep = ",";
		}
		sb.append(")");
		return sb.toString();
	}
	
	/**
	 * Copies src to dst.
	 */
	public static void copyFile(File src, File dst) throws IOException
	{
		dst.createNewFile();
		
		try (
				UnicodeBOMInputStream ubis = new UnicodeBOMInputStream(new FileInputStream(src));
				BufferedReader br = new BufferedReader(new InputStreamReader(ubis));
				BufferedWriter bw = new BufferedWriter(new FileWriter(dst)); ) {
			ubis.skipBOM();
			
			String line;
			while ((line = br.readLine()) != null)
			{
				bw.write(line);
				bw.newLine();
			}
		}
		
	}
	
	private class RetrieveThread implements Runnable {
		@Override
		public void run()
		{
			while (doRetrieveLoop)
			{
				// no date means currently running
				lastRunTime = null;
				
				// begin retrieve submissions from database
				int retrieveCount = 0;
				Vector<Integer> assignmentsToRun = new Vector<Integer>();
				
				try (Connection conn = DbConnection.getConnection(); )
				{
					PreparedStatement sql = null;
					// get a list of currently running assignments (10 min buffer)
					sql = conn.prepareStatement(
							"select assignment_id from am_assignments " +
							"where (start_time <= now() and end_time > subtime(now(), sec_to_time(600))) " +
							buildStartedAssignmentString());
					
					ResultSet rs1 = sql.executeQuery();
					while (rs1.next())
					{
						int aid = rs1.getInt("assignment_id");
						if (!stoppedAssignments.contains(aid))
							assignmentsToRun.add(aid);
					}
					
					sql.close();
					
					// only run if there are assignments
					if (assignmentsToRun.size() > 0)
					{
						// build a string of current assignments
						StringBuilder sb = new StringBuilder();
						String sep = "";
						for (int id : assignmentsToRun)
						{
							sb.append(sep);
							sb.append(id);
							sep = ", ";
						}
						String assignmentIds = sb.toString();
						
						// set the status of new submissions to in queue (backwards compatibility)
						sql = conn.prepareStatement(
								"update am_submissions set status_code = ? " +
								"where submit_time <= subtime(now(), sec_to_time(?)) " +
								"and status_code = ? and assignment_id in (" + assignmentIds + ")");
						sql.setInt(1, StatusCodes.InQueue.ordinal());
						sql.setInt(2, waitingInterval);
						sql.setInt(3, StatusCodes.NotSubmitted.ordinal());
						sql.executeUpdate();
						sql.close();
						
						// then pull all submissions that are in queue
						sql = conn.prepareStatement(
								"select submission_id from am_submissions " +
								"where status_code = ? and assignment_id in (" + assignmentIds + ")");
						sql.setInt(1, StatusCodes.InQueue.ordinal());
						
						// save to temp local buffer first - if there is nothing, don't run rsync
						List<Integer> localBuffer = new LinkedList<Integer>();
						ResultSet rs = sql.executeQuery();
						while (rs.next())
						{
							// in the interest of speed, we'll add everything we find
							// it's possible that two separate runs of retrieve will overlap
							// and the same submission will be added twice
							// this case is checked for in SubmissionWorker
							localBuffer.add(rs.getInt("submission_id"));
							retrieveCount++;
						}
						sql.close();
						
						// if we do have new submissions, run rsync first then add to proper buffer
						if (retrieveCount > 0)
						{
							// sync submission files
							int syncResult = -1;
							System.out.print("retrieval: syncing... ");
							try
							{
								Process psync = Runtime.getRuntime().exec(
										"rsync -avz /mnt/www.cs/automated-marker/Submissions/ " +
												"/home/marker/marker/courses");
								syncResult = psync.waitFor();
							}
							catch (IOException e)
							{
								// definitely means sync failed, but we'll do a run anyway
							}
							catch (InterruptedException e)
							{
								// probably means sync failed, but we'll do a run anyway
							}
							System.out.println("returned " + syncResult);
							
							// add to proper buffer
							for (Integer i : localBuffer)
							{
								submissionBuffer.offer(i);
							}
						}
					}
				}
				catch (SQLException e)
				{
					// log error and wait for next retrieval
					e.printStackTrace();
				}
				
				// set the last run time to now
				lastRunTime = new java.util.Date();
				
				System.out.println(String.format(
						"retrieval: [%s] %d retrieved, %d total, %d assignments",
						new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastRunTime),
						retrieveCount, submissionBuffer.size(), assignmentsToRun.size()));
				
				// manually check for stopping
				if (Thread.interrupted())
				{
					System.out.println("retrieval: interrupted");
					break;
				}
				
				try
				{
					Thread.sleep(waitingInterval * 1000); // required in milliseconds
				}
				catch (InterruptedException e)
				{
					// stop
					System.out.println("retrieval: interrupted");
					break;
				}
			}
		}
	}
}
