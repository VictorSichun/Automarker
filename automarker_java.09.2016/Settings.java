import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves assignment information from database and caches it locally.
 */
public class Settings
{
	private static final String BASE_PATH = "courses";
	private static final String SOLUTIONS_DIR = "solutions";
	private static final String SUBMISSIONS_DIR = "submissions";
	private static final String WORKING_DIR = "working";
	private static final String OUTPUT_STORE_DIR = "output";
	private static final String COMPARATOR = "CongruentComparator";
	
	private static Map<Integer, Settings> settingsCache = null;
	
	private int assignmentId;
	
	private String courseFullname = "";
	private int assignmentNumber = -1;
	private java.util.Date startTime = new java.util.Date();
	private java.util.Date endTime = null;
	
	private Map<Integer, ProblemInformation> problems;
	
	public static Settings getSettings(int id)
	{
		return getSettings(id, false);
	}
	
	public static Settings getSettings(int id, boolean forceUpdate)
	{
		if (settingsCache == null)
		{
			settingsCache = Collections.synchronizedMap(new HashMap<Integer, Settings>());
		}
		
		if (settingsCache.containsKey(id))
		{
			Settings s = settingsCache.get(id);
			System.out.print('0');
			
			if (forceUpdate)
			{
				s.update();
			}
			System.out.print('1');
			return s;
		}
		System.out.print('2');
		return new Settings(id);
	}
	
	private Settings(int id)
	{
		assignmentId = id;
		update();
		
		settingsCache.put(id, this);
	}
	
	// update cached information
	public boolean update()
	{
		// clear map
		problems = new HashMap<Integer, ProblemInformation>();
		
		try (Connection conn = DbConnection.getConnection(); )
		{
			PreparedStatement sql, sql2;
			ResultSet rs, rs2;
			
			// get assignment details
			sql = conn.prepareStatement(
					"select course_fullname, assignment_number, start_time, end_time " +
					"from am_assignments join am_courses using (course_id) " +
					"where assignment_id = ?");
			sql.setInt(1, assignmentId);


			rs = sql.executeQuery();

			sql2 = conn.prepareStatement("select top 1 new_end_time from am_userextensions where assignment_id = ? order by new_end_time desc");
			sql2.setInt(1, assignmentId);

			rs2 = sql2.executeQuery();

			if(rs2.next()){
				endTime = rs2.getTimestamp("new_end_time");
			}
			if (rs.next()) // there will only be a maximum of 1 result
			{
				courseFullname = rs.getString("course_fullname");
				assignmentNumber = rs.getInt("assignment_number");
				startTime = rs.getTimestamp("start_time");
				if(endTime == null) {
					endTime = rs.getTimestamp("end_time");
				}
			}
			else
			{
				throw new IllegalArgumentException("Invalid assignment id.");
			}
			try { sql.close(); sql2.close(); } catch (SQLException ignoredException) {}
			
			// get problem details
			sql = conn.prepareStatement("select * from am_problems where assignment_id = ?");
			sql.setInt(1, assignmentId);
			
			rs = sql.executeQuery();
			while (rs.next())
			{
				int id = rs.getInt("problem_id");
				problems.put(id, new ProblemInformation(
						id,
						rs.getString("problem_name"),
						rs.getInt("problem_timelimit"),
						COMPARATOR, // for backwards compatibility
						rs.getString("problem_input"),
						rs.getString("problem_output"),
						rs.getInt("problem_readfileflag")
						));
			}
			try { sql.close(); } catch (SQLException ignoredException) {}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			System.out.print("false");
			return false;
		}
		System.out.print("true");
		return true;
	}
	
	// unique id for this assignment
	public int getAssignmentId()
	{
		return assignmentId;
	}
	
	// assignment information
	public java.util.Date getStartTime()
	{
		return startTime;
	}
	
	public java.util.Date getEndTime()
	{
		return endTime;
	}
	
	// problem specific information
	public String[] getProblemNames()
	{
		List<String> names = new ArrayList<String>();
		
		for (int id : problems.keySet())
		{
			names.add(problems.get(id).getName());
		}
		
		return names.toArray(new String[0]);
	}
	
	public boolean containsProblem(int problem)
	{
		return problems.containsKey(problem);
	}
	
	public String getProblemName(int problem)
	{
		return problems.containsKey(problem) ? problems.get(problem).getName() : null;
	}
	
	public int getTimeLimit(int problem)
	{
		return problems.containsKey(problem) ? problems.get(problem).getTimeLimit() : -1;
	}

	public int getReadFileFlag(int problem)
	{
		return problems.containsKey(problem) ? problems.get(problem).getReadFileFlag() : -1;
	}
	
	public String getComparator(int problem)
	{
		return problems.containsKey(problem) ? problems.get(problem).getComparator() : null;
	}
	
	public String getInputFilePath(int problem)
	{
		return problems.containsKey(problem) ? String.format("%s/%s", getSolutionsDir(), problems
				.get(problem).getInput()) : null;
	}
	
	public String getOutputFilePath(int problem)
	{
		return problems.containsKey(problem) ? String.format("%s/%s", getSolutionsDir(), problems
				.get(problem).getOutput()) : null;
	}
	
	public boolean hasInputOutputFiles(int problem)
	{
		return problems.containsKey(problem)
				&& !problems.get(problem).getInput().trim().equals("")
				&& !problems.get(problem).getOutput().trim().equals("");
	}
	
	// directory related methods
	public String getSolutionsDir()
	{
		return String.format("%s/%s", getBaseDirectory(), SOLUTIONS_DIR);
	}
	
	public String getSubmissionsDir()
	{
		return String.format("%s/%s", getBaseDirectory(), SUBMISSIONS_DIR);
	}
	
	public String getWorkingDir()
	{
		return String.format("%s/%s", getBaseDirectory(), WORKING_DIR);
	}
	
	public String getOutputStoreDir()
	{
		return String.format("%s/%s", getBaseDirectory(), OUTPUT_STORE_DIR);
	}
	
	// internal helper methods
	private String getBaseDirectory()
	{
		return String.format("%s/%s.%d", BASE_PATH, courseFullname, assignmentNumber);
	}
	
	// container for problem information
	public class ProblemInformation
	{
		private int problemId = -1;
		private String name = "";
		private int timeLimit = -1;
		private String comparator = "";
		private String input = "";
		private String output = "";
		private int readFileFlag = -1;
		
		public ProblemInformation(int id, String name, int timeLimit, String comparator,
				String input, String output, int readFileFlag)
		{
			problemId = id;
			this.name = name;
			this.timeLimit = timeLimit;
			this.comparator = comparator;
			this.input = input;
			this.output = output;
			this.readFileFlag = readFileFlag;
		}
		
		public int getProblemId()
		{
			return problemId;
		}
		
		public String getName()
		{
			return name;
		}
		
		public int getTimeLimit()
		{
			return timeLimit;
		}
		
		public String getComparator()
		{
			return comparator;
		}
		
		public String getInput()
		{
			return input;
		}
		
		public String getOutput()
		{
			return output;
		}

		public int getReadFileFlag() { return readFileFlag; }
	}
}
