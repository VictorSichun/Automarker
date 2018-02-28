import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class SubmissionVerifier {
    private File standardInput;
    private File studentOutput;
    private File verifier;
    private String rawCommand;
    private String xmlFile = "compilers.xml";
    private static String[][] languageOptions = null;
    private static long lastModified = 0;
    private static final int RUN = 3;
    private Process proc;
    private String error = "";

    public SubmissionVerifier(File verifier, File standardInput, File studentOutput) {
        loadXml();
        this.standardInput = standardInput;
        this.studentOutput = studentOutput;
        this.verifier = verifier;
    }

    private void loadXml() {
        try {
            File f = new File(xmlFile);
            if (f.lastModified() > lastModified) {
                lastModified = f.lastModified();
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setIgnoringComments(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document xmlDoc = builder.parse(f);
                xmlDoc.getDocumentElement().normalize();
                NodeList compilerStuff = xmlDoc.getElementsByTagName("Compilers");

                Element e = (Element) compilerStuff.item(0);
                compilerStuff = e.getElementsByTagName("*");

                languageOptions = new String[(compilerStuff.getLength()) / 6][1];
                int count = 0;
                for (int i = 0; i < compilerStuff.getLength(); i = i + 6) {
                    e = (Element) compilerStuff.item(i);

                    NodeList nodes = e.getElementsByTagName("*");
                    languageOptions[count][0] = nodes.item(RUN).getTextContent();
                    count++;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int verify(int programmingLanguage){
        int exitValue = -1;
        try{
            String runCmd = languageOptions[programmingLanguage][0];
            runCmd = runCmd.replace("basename.parent", verifier.getParent().replace("\\", "/"));
            runCmd = runCmd.replace("basename", verifier.getPath().replace("\\", "/"));
            runCmd = runCmd.replace("absolutepath",
                    verifier.getParentFile().getAbsolutePath().replace("\\", "/"));
            runCmd = runCmd.replace("problem", getProblemName(verifier.getName()));

            runCmd += " " + studentOutput.getPath();

            System.out.println("verifying [" + verifier.getName() + "]: " + runCmd);
            rawCommand = runCmd;
            ProcessBuilder pb = new ProcessBuilder(runCmd.split(" "));
            pb.redirectInput(standardInput);
            try {
                proc = pb.start();
                System.out.println("verifier: start verifying...");
            } catch (IOException e) {
                System.out.println("verifier: Failed to start. Assume broken redirection pipe");
                e.printStackTrace();
            }

            try {
                exitValue = proc.waitFor(); // blocking

            } catch (InterruptedException ignoredException) {}
            System.out.println("verifier: process finished with exitValue=" + exitValue);
            redirectError();
            return exitValue;
        }catch (Exception e){
            e.printStackTrace();
            return exitValue;
        }
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

    /** Gets the verifier's error output. Should call {@link #close()} first. */
    public String getError()
    {
        String trimmed = error.trim();
        System.out.println("Getting error. Trimmed error string is:\n" + trimmed);
        // last line is output from "time" so we need to remove it
        int index = trimmed.lastIndexOf('\n') + 1;
        return trimmed.substring(0, index).trim();
    }

    /** Gets the verifier's output. Should call {@link #close()} first. */
    public String getResult()
    {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(proc.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        String result = "";
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
                result = builder.toString();
            }
        }catch(IOException e){
            return result;
        }

        return result;
    }

    public void close()
    {
        try
        {
            System.out.println(rawCommand);
            Runtime.getRuntime().exec(new String[] { "pkill", "-f", rawCommand });

        }
        catch (IOException e)
        {
            System.out.println("Exception caught while killing subprocess");
            e.printStackTrace();
        }
        // proc.destroy(); // this is unnecessary because "pkill" already kills it
    }


    private String getProblemName(String original){
        int index = original.lastIndexOf(".");
        return original.substring(0, index);
    }
}
