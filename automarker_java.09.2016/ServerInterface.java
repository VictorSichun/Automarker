import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;

public interface ServerInterface extends Remote
{
	public int getAssignmentId(String name) throws RemoteException;
	
	public boolean startMarker(int id) throws RemoteException;
	public boolean stopMarker(int id) throws RemoteException;
	public boolean resetMarker(int id) throws RemoteException;
	
	public String[] getRunningAssignments() throws RemoteException;
	
	public int setWaitingInterval(int seconds) throws RemoteException;
	public int getWaitingInterval() throws RemoteException;
	public Date getLastRunTime() throws RemoteException;
	
	public void forceRun() throws RemoteException;
}
