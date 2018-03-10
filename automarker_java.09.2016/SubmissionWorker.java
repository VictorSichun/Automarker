
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Queue;

public class SubmissionWorker implements Runnable
{
	private static final int SLEEP_TIME = 5000;
	
	private Queue<Integer> submissionBuffer;
	private boolean doLoop;
	private static final long MAX_FILE_SIZE = 3000;
	private final int TXTFILE = -1;
	private final String VERIFIER_ERROR_MESSAGE = "Please contact with administrator";
	private static long lastModified = 0;
	
	public SubmissionWorker(Queue<Integer> buffer)
	{
		submissionBuffer = buffer;
		doLoop = true;
	}
	
	public void stop()
	{
		doLoop = false;
	}
	
	@Override
	public void run()
	{
		// for sleep status message
		boolean firstTime = true;
		
		while (doLoop)
		{
			// id of the current submission we are processing
			int id = -1;
			
			// for sleep status message
			boolean processedSomething = false;
			
			try
			{
				while (submissionBuffer.peek() != null)
				{
					processedSomething = true;
					
					Integer io = submissionBuffer.poll();
					
					// make sure no other thread has stolen the object we peeked
					if (io == null)
					{
						break;
					}
					
					// get information for this submission
					id = io.intValue();
					
					System.out.println("worker: got submission " + id);
					
					// information that we will be getting from the database
					int assignmentId = -1;
					int problemId = -1;
					String username = "";
					String submissionName = "";
					
					try (
							Connection conn = DbConnection.getConnection(); )
					{
						PreparedStatement sql = conn.prepareStatement(
								"update am_submissions set status_code = ? " +
								"where submission_id = ? and status_code = ?");
						// important to check if status is still 'in queue' since the submission
						// might be retrieved more than once by the retriever thread
						sql.setInt(1, StatusCodes.InProgress.ordinal());
						sql.setInt(2, id);
						sql.setInt(3, StatusCodes.InQueue.ordinal());
						int rowsModified = sql.executeUpdate();//An int that indicates the number of rows affected, or 0 if using a DDL statement.
						sql.close();
						
						if (rowsModified == 0)
						{
							// we didn't find anything - someone else is processing it
							throw new InternalWorkerException("worker: someone stole submission " + id, null);
						}
						
						// now that we have reserved the submission for ourselves
						// we'll retrieve its information
						sql = conn.prepareStatement(
								"select assignment_id, problem_id, username, submission_name " +
								"from am_submissions where submission_id = ?");
						sql.setInt(1, id);
						
						ResultSet rs = sql.executeQuery();
						if (!rs.next())
						{
							// something went wrong
							throw new InternalWorkerException("worker: couldn't look up submission " + id);
						}
						
						// fill in the variables
						assignmentId = rs.getInt("assignment_id");
						problemId = rs.getInt("problem_id");
						username = rs.getString("username");
						submissionName = rs.getString("submission_name");
						
						sql.close();
					}
					catch (SQLException e)
					{
						// internal error, keep going
						e.printStackTrace();
						throw new InternalWorkerException();
					}
					
					// check if the correct problem has been identified
					if (problemId == 0)
					{
						// php script failed to match submission name with any problem names
						throw new InternalWorkerException(
								"worker: incorrect name for submission " + id,
								StatusCodes.IncorrectName);
					}
					
					// get relevant information about this submission
					// make sure the information is the latest available
					Settings settings = Settings.getSettings(assignmentId, true);
					// if there are no input/output files, assume this means marking is not required
					if (!settings.hasInputOutputFiles(problemId))
					{
						// mark as correct and skip
						throw new InternalWorkerException(
								"worker: submission does not require marking",
								StatusCodes.Submitted);
					}
					
					// get file path to this submission
					String submissionPath = String.format("%s/%s/%s",
							settings.getSubmissionsDir(), username, submissionName);
					String workingPath = String.format("%s/%s", settings.getWorkingDir(), submissionName);
					
					File submissionFile = new File(submissionPath);
					File workingFile = new File(workingPath);
					
					// rsync might not have copied the submission directory over yet
					if (!submissionFile.exists())
					{
						// ignore this for now; we'll get it on the next run
						throw new InternalWorkerException(
								"worker: cannot find file for submission " + submissionPath + " " + id);
					}
					
					// copy file to working directory
					try
					{
						Server.copyFile(submissionFile, workingFile);
					}
					catch (IOException e)
					{
						// internal error
						throw new InternalWorkerException("worker: error copying '"
								+ submissionPath + "' to '" + workingPath + "'");
					}



					
					// change status to in progress
					updateStatus(id, StatusCodes.InProgress);
					// clear extra information
					updateExtraInformation(id, "");
					
					// get the name of the problem
					String problemName = settings.getProblemName(problemId);
					
					if (problemName == null)
					{
						// this should never happen unless assignmentId and problemId don't
						// match which should also never happen
						throw new InternalWorkerException(
								"worker: could not get problem name for submission " + id,
								StatusCodes.Other);
					}
					
					// variables for holding marking information
					String extraInformation = null;
					StatusCodes statusCode = null;
					float executionTime = -1;
					
					// compile
					SourceCodeFile scf = new SourceCodeFile(workingPath, problemName);
					System.out.println("worker: compiling submission " + id);
					// limit the source file size under 3000 bytes.
					if (submissionFile.length() > MAX_FILE_SIZE) {
						updateExtraInformation(id,"Source file size exceeded.");
						statusCode = StatusCodes.CompilerError;

					}else{

						int compileStatus = scf.compile();
						if (compileStatus == -1) {
							// couldn't recognise language
							updateExtraInformation(id, "Unknown programming language.");
							throw new InternalWorkerException(
									"worker: unrecognised language for submission " + id, StatusCodes.Other);
						} else if (compileStatus == -2) {
							// couldn't copy file - out of space, etc.
							// internal error, so leave the file for next round
							throw new InternalWorkerException(
									"worker: could not copy file while compiling for submission " + id);
						} else if (compileStatus != 0) {
							// compiler returned error
							// set variables and skip rest
							extraInformation = scf.getError();
							statusCode = StatusCodes.CompilerError;
						} else {
							// run
							File output = new File(settings.getWorkingDir(),
									settings.getProblemName(problemId) + ".out");

							// check read file method flag, copy the input file into working directory if the method was enabled
							File input = new File(settings.getInputFilePath(problemId));
							if(settings.getReadFileFlag(problemId) == 1){
								// constructing destination for copying
								String inputFileWorkingPath = String.format("%s/%s", settings.getWorkingDir(), input.getName());
								File inputFile = new File(inputFileWorkingPath);
								System.out.println("worker: read file flag enabled, copying input file to working directory...");
								try{
									Server.copyFile(input, inputFile);
									input = inputFile;
								}
								catch (IOException e)
								{
									// internal error
									throw new InternalWorkerException("worker: error copying '"
											+ input.getPath() + "' to '" + inputFileWorkingPath + "'");
								}
							}


							System.out.println("worker: executing submission " + id);

							int executeStatus = scf.execute(output, input,
									settings.getTimeLimit(problemId));
							executionTime = scf.getExecTime();
							// System.out.println("worker: time " + executionTime);

							if (executeStatus != 0) {
								// run time error
								extraInformation = scf.getError();
								statusCode = StatusCodes.RunTimeError; // but may be time limit
							}
							if (executeStatus == -2) {
								// out of time
								statusCode = StatusCodes.TimeLimitExceeded;
							} else if (executeStatus == 0) {
								// compare output
								File solution = new File(settings.getOutputFilePath(problemId));
								// checking if the solution file is a txt file or a verifier
								int language = scf.evalProgrammingLanguage(solution.getName());
								// solution file is a txt file, then we do comparison
								if(language == TXTFILE) {
									statusCode = CongruentComparator.compare(output, solution);
									if (statusCode == StatusCodes.WrongAnswer) {
										if (CongruentComparator.diffpercent > 0) {
											extraInformation = "Output lines correct = " + CongruentComparator.diffpercent + "%\n";

										} else if (CongruentComparator.diffpercent == -1) {
											extraInformation = "Number of output lines not as expected.\n";
										}
										//else
										//{
										//	extraInformation = "Totally wrong\n";
										//}
									}
								}else{
									// otherwise, the solution file is a verifier
									// first check if the verifier has been modified
									int verifierStatus = 0;
									if(lastModified < solution.lastModified()) {
										lastModified = solution.lastModified();
										// if true, then compile verifier; otherwise, the verifier does not need to be compiled again
										SourceCodeFile scfForVerifier = new SourceCodeFile(solution.getPath(), problemName);
										System.out.println("worker: compiling the verifier...");
										verifierStatus = scfForVerifier.compile(1);
										System.out.println("worker: compilation completes, verifier status code" + verifierStatus);
									}
									if(verifierStatus == 0) {
										int exitValue = -1;
										SubmissionVerifier sv = new SubmissionVerifier(solution, input, output);
										exitValue = sv.verify(language);
										if (exitValue == 0) {
											System.out.println("verifier: the answer seems okay!");
											statusCode = StatusCodes.Correct;
										} else if (exitValue == 1) {
											// some lines are bad
											sv.close();
											if (sv.getError().equals("")) {
												System.out.println("verifier: answers are not correct!");
												statusCode = StatusCodes.WrongAnswer;
												extraInformation = sv.getResult();
											} else {
												System.out.println("verifier: I got something wrong");
												statusCode = StatusCodes.Other;
												extraInformation = VERIFIER_ERROR_MESSAGE;
											}
										} else {
											System.out.println("verifier: something goes wrong with SubmissionVerifier class");
											statusCode = StatusCodes.Other;
											extraInformation = VERIFIER_ERROR_MESSAGE;
										}
									}else{
										System.out.println("verifier: error occurs while compiling");
										statusCode = StatusCodes.Other;
										extraInformation = VERIFIER_ERROR_MESSAGE;
									}
								}

								if (output.length() < 1000000) // about a meg max size to store output
								{
									// save the output file
									File store = new File(settings.getOutputStoreDir(), submissionName
											+ "+" + problemName + ".out");
									try {
										Server.copyFile(output, store);
									} catch (IOException e) {
										// non critical internal error
										System.out.println("worker: error copying '" + output.getPath()
												+ "' to '" + store.getPath() + "'");
									}
								}
							}
						}
					}
					// update database with completed information
					try (
							Connection conn = DbConnection.getConnection();
							PreparedStatement sql = conn.prepareStatement(
									"update am_submissions set completed_time = now() " +
									"where submission_id = ?"); )
					{
						// update status code, extra information, execution time fields
						updateStatus(id, statusCode);
						
						if (extraInformation != null)
						{
							if(extraInformation.length() > 99)
							{
								updateExtraInformation(id, extraInformation.substring(0,99)); // mjd guess Sept26, 2016
							}else{
								updateExtraInformation(id, extraInformation);
							}
						}
						
						if (executionTime >= 0)
						{
							updateExecTime(id, executionTime);
						}
						
						// update time
						sql.setInt(1, id);
						
						sql.executeUpdate();
					}
					catch (SQLException e)
					{
						// internal error, keep going
						e.printStackTrace();
						throw new InternalWorkerException();
					}
					
					System.out.println("worker: submission " + id + " marked with status "
							+ statusCode.toString());
				}
				
				// manually check for stopping
				if (Thread.interrupted())
				{
					break;
				}
				
				// message
				if (firstTime || processedSomething)
				{
					firstTime = false;
					System.out.println("worker: sleeping...");
				}
				
				try
				{
					Thread.sleep(SLEEP_TIME);
				}
				catch (InterruptedException e)
				{
					// stop
					break;
				}
				
			}
			catch (InternalWorkerException e)
			{
				if (e.getMessage() != null)
				{
					System.out.println(e.getMessage());
				}
				
				// set this back to being processed
				if (id != -1 && e.getStatusCode() != null)
				{
					updateStatus(id, e.getStatusCode());
				}
			}
			catch (Exception e)
			{
				// don't let worker thread die
				System.out.println("worker: Uncaught exception.");
				e.printStackTrace();
			}
		}
	}
	
	private static void updateExecTime(int id, float time)
	{
		try (
				Connection conn = DbConnection.getConnection();
				PreparedStatement sql = conn.prepareStatement(
						"update am_submissions set execution_time = ? where submission_id = ?"); )
		{
			sql.setInt(1, (int) (time * 1000));
			sql.setInt(2, id);
			
			sql.executeUpdate();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	private static void updateExtraInformation(int id, String extra)
	{
		try (
				Connection conn = DbConnection.getConnection();
				PreparedStatement sql = conn.prepareStatement(
						"update am_submissions set extra = ? where submission_id = ?"); )
		{
			sql.setString(1, extra);
			sql.setInt(2, id);
			
			sql.executeUpdate();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	private static void updateStatus(int id, StatusCodes status)
	{
		try (
				Connection conn = DbConnection.getConnection();
				PreparedStatement sql = conn.prepareStatement(
						"update am_submissions set status_code = ? where submission_id = ?"); )
		{
			sql.setInt(1, status.ordinal());
			sql.setInt(2, id);
			
			sql.executeUpdate();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}

	
	// dummy class for processing all status codes correctly
	private class InternalWorkerException extends Exception
	{
		private static final long serialVersionUID = 1L;
		private StatusCodes code;
		
		public InternalWorkerException()
		{
			this(null, StatusCodes.InQueue);
		}
		
		public InternalWorkerException(String message)
		{
			this(message, StatusCodes.InQueue);
		}
		
		public InternalWorkerException(String message, StatusCodes code)
		{
			super(message);
			this.code = code;
		}
		
		public StatusCodes getStatusCode()
		{
			return code;
		}
	}
}
