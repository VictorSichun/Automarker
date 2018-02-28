import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnection
{
	// private static String URL = "jdbc:mysql://cs-db.cs.auckland.ac.nz/cs_automarker";
	// private static String URL = "jdbc:mysql://localhost/cs_automarker";
	private static String URL = "jdbc:mysql://helix-gate.sit.auckland.ac.nz/cs_automarker";
	private static String USERNAME = "cs_automarker";
	private static String PASSWORD = "sdfkjh8965657";
	
	static
	{
		try
		{
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException e)
		{
			System.out.println("Fatal error: could not load database driver.");
			e.printStackTrace();
			System.exit(1);
		}
		
		// test connection
		
		try (Connection conn = getConnection(); )
		{}
		catch (SQLException e)
		{
			System.out.println("Fatal error: could not establish connection to database.");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static Connection getConnection() throws SQLException
	{
		return DriverManager.getConnection(URL, USERNAME, PASSWORD);
	}
}
