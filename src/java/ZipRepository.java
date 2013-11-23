import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ZipRepository {
  private String      resourceFile;
  private ZipRecord[] zips;
  private HashMap<String,ZipRecord> htZips;

  private static String getInnerText(Element el,String tag) {
    Node n = el.getElementsByTagName(tag).item(0);

    return n.hasChildNodes() ?
      n.getFirstChild().getNodeValue() :
      null;
  }

  public Document getConfigDoc(InputStream file,boolean validate) {
    DocumentBuilderFactory factory =
      DocumentBuilderFactory.newInstance();
    factory.setValidating(validate);
    //factory.setNamespaceAware(true);
    try {
       DocumentBuilder builder = factory.newDocumentBuilder();
       return builder.parse(file);
    } catch(Throwable t) {
    }

    return null;
  }

  public ZipRepository(String input_file) throws IOException,SAXException {
    htZips = new HashMap<String,ZipRecord>();

    ZipRecord zip;

    ArrayList<ZipRecord> v_zips = new ArrayList<ZipRecord>();
    ArrayList<EntryPoint> v_ep  = new ArrayList<EntryPoint>();
    int n_entry_points;

    NodeList    nl,snl;
    Element     el,sel;

    InputStream in = new FileInputStream(input_file);

    Document document = getConfigDoc(in, false);
    in.close();

    System.out.println("ZipRepository:Loading:" + input_file);

    resourceFile =
      ((Element)
            ((Element) document.
             getElementsByTagName("ResourceZip").
             item(0)).
            getElementsByTagName("File").
            item(0)).
      getFirstChild().
      getNodeValue();

    zip = new ZipRecord();
    zip.virtualDir        = ".resource";
    zip.file              = resourceFile;
    zip.description       = "Resource file";
    zip.defaultEntryPoint = null;

    htZips.put(".resource",zip);

    //System.out.println("resource file: .resource -> "+resourceFile);

    nl = ((Element) document.getElementsByTagName("ZipList").item(0)).
      getElementsByTagName("ZipFile");
    int n_zips = nl.getLength();

    for(int i=0;i<n_zips;i++) {
      el = (Element) nl.item(i);

      String vdir = getInnerText(el,"VDir");
      String file = getInnerText(el,"File");

      //System.out.println("xml:vdir:"+vdir+" - File:"+file);

      if(vdir == null || file == null)
        continue;

      zip = new ZipRecord();

      zip.virtualDir        = vdir;
      zip.file              = file;
      zip.description       = getInnerText(el,"Description");
      zip.defaultEntryPoint = getInnerText(el,"DefEntryPoint");

      //System.out.println("xml:desc:"+zip.description+" - dep:"+zip.defaultEntryPoint);

      snl = el.getElementsByTagName("DefEntryPoint");
      if(snl != null && snl.getLength() > 0) {
        for(int j=0 ; j<snl.getLength() ; j++) {
          sel = (Element) snl.item(j);

          if(sel.getElementsByTagName("RelURL").item(0).hasChildNodes()) {
            zip.defaultEntryPoint = getInnerText(sel,"RelURL");
          }
        }
      }

      //System.out.println("xml:desc:"+zip.description+" - dep:"+zip.defaultEntryPoint);

      snl = el.getElementsByTagName("EntryPoint");

      if(snl != null && (n_entry_points = snl.getLength()) > 0) {
        for(int j=0 ; j<n_entry_points ; j++) {
          sel = (Element) snl.item(j);

          if(sel.getElementsByTagName("RelURL").item(0).hasChildNodes()) {
            EntryPoint ep = new EntryPoint();

            ep.url         = getInnerText(sel,"RelURL");
            ep.description = getInnerText(sel,"Description");

            v_ep.add(ep);
          }
        }

        if(v_ep.size() != 0) {
          zip.entryPoints = new EntryPoint[v_ep.size()];
          v_ep.toArray(zip.entryPoints);
          v_ep.clear();
        }
      }

      htZips.put(zip.virtualDir,zip);

      v_zips.add(zip);
    }

    //System.out.println(htZips);

    v_zips.toArray(zips = new ZipRecord[v_zips.size()]);
  }

  public void writeIniFile(PrintWriter out) {
    out.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"yes\"?>");
    out.println();
    out.println("<!DOCTYPE ZipViewList [");
    out.println("  <!ELEMENT ZipViewList (ResourceZip,ZipList)>");
    out.println();
    out.println("  <!ELEMENT ResourceZip (File)>");
    out.println("  <!ELEMENT ZipList (ZipFile*)>");
    out.println();
    out.println("  <!ELEMENT ZipFile (VDir,File,Description,DefEntryPoint,EntryPoint*)>");
    out.println("  <!ELEMENT VDir (#PCDATA)>");
    out.println("  <!ELEMENT File (#PCDATA)>");
    out.println("  <!ELEMENT Description (#PCDATA)>");
    out.println("  <!ELEMENT DefEntryPoint (RelURL)>");
    out.println("  <!ELEMENT EntryPoint (RelURL,Description)>");
    out.println("  <!ELEMENT RelURL (#PCDATA)>");
    out.println("  <!ELEMENT RelURL (#PCDATA)>");
    out.println("]>");
    out.println();
    out.println("<ZipViewList>");
    out.println("  <ResourceZip>");
    out.println("    <File>" + resourceFile + "</File>");
    out.println("  </ResourceZip>");
    out.println();
    out.println("  <ZipList>");
    out.println("  </ZipList>");
    out.println("<ZipViewList>");
  }

  public ZipRecord getZipRecord(String vDir) {
    return (ZipRecord) htZips.get(vDir);
  }

  public ZipRecord[] getZips() {
    ZipRecord[] m_zips;

    synchronized(this) {
      m_zips = new ZipRecord[zips.length];
      System.arraycopy(zips,0,m_zips,0,zips.length);
    }

    return m_zips;
  }
}
