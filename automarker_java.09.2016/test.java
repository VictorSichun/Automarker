import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class test {

    private String xmlFile = "compilers.xml";
    private static String[][] languageOptions = null;
    private static long lastModified = 0;
    public static final int RUN = 3;
    public static void main(String[] arg){
        for(int i = 0; i < 10; i++) {
            test t = new test();

            t.loadXml();

        }

    }

    public void loadXml() {
        try {
            File f = new File(xmlFile);
            if(languageOptions == null){
                System.out.println("null");
            }
            if(lastModified != 0){
                System.out.println(lastModified);
            }
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

                languageOptions = new String[(compilerStuff.getLength()) / 6][1];
                int count = 0;
                for (int i = 0; i < compilerStuff.getLength(); i = i + 6) {
                    e = (Element) compilerStuff.item(i);

                    NodeList nodes = e.getElementsByTagName("*");
                    languageOptions[count][0] = nodes.item(RUN).getTextContent();
                    count++;
                }
            }
            System.out.println(languageOptions[0][0]);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
