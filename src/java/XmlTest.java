import java.io.IOException;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Test for R/W Xml zipview files
 */
public class XmlTest {
  public static void main(String[] args) throws IOException, SAXException {
    try {
      new ZipRepository("conf/zipfiles.xml");
    } catch(SAXParseException se) {
      System.out.println("SAXParseException-"+se.getLineNumber()+":"+se.getColumnNumber()+"|"+se);
    } catch(SAXException se) {
      System.out.println("SAXException:"+se);
    }
  }
}
