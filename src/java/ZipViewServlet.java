import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Serves files inside zip archives transparently.
 * Uses a minimum set of classes for improving performance.
 */
public class ZipViewServlet extends HttpServlet {
  private static Logger logger =
      Logger.getLogger(ZipViewServlet.class.getName());

  /**
   * File holding configuration info (XML format)
   */
  String initXmlFile;
 
  /**
   * Indicates an error when initializing the servlet
   */
  boolean init_error=false;
 
  /**
   * Attributes of the body tag (for browsing directories)
   */
  String body_props="bgcolor=#000080 text=#ffffff link=#ffff00 vlink=#ff8000";
 
  /**
   * Error message for an unspecified zip file (??)
   */
  static final String no_zip_msg="No zip file specified";
 
  /**
   * Error message for an erroneous requested zip alias
   */
  static final String zip_not_found_msg="Zip file not found";
 
  /**
   * Mime types of served files
   */
  static final String[][] mime_types = {
    {"txt","text/plain"},
    {"java","text/plain"},
    {"htm","text/html"},
    {"html","text/html"},
    {"xml","text/xml"},
    {"xsl","text/xml"},
    {"bmp","image/bmp"},
    {"gif","image/gif"},
    {"jpg","image/pjpeg"},
    {"jpeg","image/pjpeg"},
    {"png","image/png"},
    {"css","text/css"},
    {"pdf","application/pdf"},
    {"js","application/javascript"},
    {"ps","application/postscript"}
  };
 
  /**
   * Optimization of mime_types.
   */
  static final Map<String,String> hash_mime_types;
 
  /**
   * Guess mime type of file
   */
  public static final int RENDER_NORMAL = 0;
 
  /**
   * Show file as plain text
   */
  public static final int RENDER_TEXT   = 1;
 
  /**
   * Show file as hex/ascii
   */
  public static final int RENDER_HEX    = 2;
 
  /**
   * Hex values
   */
  public static final char[] hex_values =
    {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
 
  /**
   * HTML to shown for viewing chars < 32
   */
  String control_char = "<font color=\"red\">.</font>";
 
  /**
   * New Zip file repository
   */
  ZipRepository zipRepository;
 
  /**
   * Flusher thread
   */
  Thread flusher = null;

  static {
    HashMap<String,String> hm = new HashMap<String,String>();
    for(int i=0;i<mime_types.length;i++)
      hm.put(mime_types[i][0],mime_types[i][1]);
    hash_mime_types = Collections.unmodifiableMap(hm);
  }

  /**
   * Servlet constructor
   */
  public ZipViewServlet() {
      super();
  }
 
  /**
   * Servlet initialization method
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    //initXmlFile = getInitParameter("initXmlFile");
    initXmlFile = System.getProperty("user.home") + File.separator + ".zipviewservlet.conf";

    logger.fine("Reading init from: " + initXmlFile);

    zipRepository = null;
    if(initXmlFile != null) {
      readInitFile();
      logger.fine("Read init.");
    }
  }
 
  /**
   * Read the initialization file
   */
  public void readInitFile() {
    zipRepository = null;
    if(flusher != null) {
      // Avisa a thread para morrer
      synchronized(this) {
        notify();
      }

      // Espera que a thread morra
      synchronized(this) {
        while(flusher != null) {
          try {
            wait();
          } catch (InterruptedException e) {
          }
        }
      }
    }
   
    try {
      zipRepository = new ZipRepository(initXmlFile);
    } catch(SAXParseException se) {
      System.out.println("SAXParseException-" +
                         se.getLineNumber() + ":" +
                         se.getColumnNumber() + "|" + se);
    } catch(SAXException se) {
      System.out.println("SAXException:" + se);
    } catch(IOException e) {
      System.out.println("IOException:" + e);
    }

    flusher = new Thread(new ResourceFlusher());
    flusher.setDaemon(true);
    flusher.start();
  }
 
  /**
   *
   */
  public class ResourceFlusher implements Runnable {
    public void run() {
      while(true) {
        synchronized(ZipViewServlet.this) {
          try {        
            ZipViewServlet.this.wait(900000);
            //ZipViewServlet.this.wait(5000);
          } catch (InterruptedException e) {
          }
        }
       
        if(zipRepository == null) {
          //System.out.println("Dying...");
          synchronized(ZipViewServlet.this) {
            flusher = null;
            ZipViewServlet.this.notify();
          }
          return;
        }
       
        //System.out.println("Flushing...");
        ZipRecord[] zips = zipRepository.getZips();
        for(int i=0;i<zips.length;i++) {
          zips[i].flushZips();
          zips[i] = null;
        }
        zips = null;
      }
    }
  }
 
  /**
   * Returns a complete URL to the servlet:<br>
   *   For a http://myhost:8001/myservlet/dir/?parameters url
   *   returns http://myhost:8001/myservlet<br>
   * It is extremely usefull for redirects
   */
  public StringBuffer getFullServletPath(HttpServletRequest req) {
    StringBuffer sb = new StringBuffer();

    sb.append(req.getScheme()).append("://");
    String server = req.getHeader("REMOTE_ADDR");
    if(server != null && server.length()!=0)
      sb.append(server);
    else
      sb.append(req.getServerName());
    int port = req.getServerPort();
    if(port != 80)
      sb.append(':').append(port);
    sb.append(req.getContextPath())
      .append(req.getServletPath());

    return sb;
  }
 
  /**
   * Returns the alias component of a zip file URL:
   *   For a http://myhost/myservlet/dir1/dir2/dir.../file?parameters
   *   returns dir1
   * @param path_info The path info of the requested URL.
   * @see getPathInfo()
   */
 
  public String getRequestedAlias(String path_info) {
    if(path_info == null || path_info.length() == 0 || path_info.length() == 1)
      return null;
    
    int t = path_info.indexOf('/',1);
    if(t == -1)
      return path_info.substring(1);
    else
      return path_info.substring(1,t);
  }
 
  /**
   * Returns the file/path component of a zip file URL:
   *   For a http://myhost/myservlet/dir1/dir2/dir.../file?parameters
   *   returns dir2/dir.../file
   * @param path_info The path info of the requested URL.
   * @see getPathInfo()
   */
 
  public String getRequestedFile(String path_info) {
    if(path_info.length()==0 || path_info.length()==1)
      return null;
    
    int t = path_info.indexOf('/',1);
    if(t == -1)
      return null; // No file was requested
    else
      return path_info.substring(t+1);
  }
 
  /**
   * Returns the MIME type of a served file
   */
  public void guessContentType(String filename,HttpServletResponse res) {
    int dp=filename.lastIndexOf('.');

    if(dp==-1)
      return;
 
    Object mime_type = hash_mime_types.get(filename.substring(dp+1).
                                           toLowerCase());
 
    if(mime_type != null)
      res.setContentType((String) mime_type);
  }
 
  /**
   * Servlet GET method
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {

    ZipRecord           zip_file=null;
    String              zip_alias;
    String              zipped_filename=null;
    ZipFile             zip=null;
    ServletOutputStream out=null;
    ZipEntry            zip_entry=null;
    InputStream         zipped_file=null;
    int                 _unused_t;
    String              query_string = req.getQueryString();
    int                 render       = RENDER_NORMAL;

    HttpSession session = req.getSession(true);

    boolean browsing = session.getAttribute("browsemode")!=null;
    out = res.getOutputStream();

    String path_info=req.getPathInfo();

    if(query_string!=null && query_string.length()!=0) {
      if("reload".equals(query_string)) {
        readInitFile();

        res.sendRedirect(getFullServletPath(req).append('/').toString());
        return;
      } else if("browse".equals(query_string)) {
        session.setAttribute("browsemode",new Boolean(true));
        res.sendRedirect(getFullServletPath(req).append('/').toString());
      } else if("nobrowse".equals(query_string)) {
        session.removeAttribute("browsemode");
        res.sendRedirect(getFullServletPath(req).append('/').toString());
      } else if("text".equals(query_string)) {
        render = RENDER_TEXT;
      } else if("hex".equals(query_string)) {
        render = RENDER_HEX;
      } else if("sidebar".equals(query_string)) {
        sidebar(req,res);
        return;
      } else if("browseall".equals(query_string)) {
        zip_alias = getRequestedAlias(path_info);

        if(zip_alias == null) {
          showZips(null,req,res);
          return;
        }

        zip_file=(ZipRecord) zipRepository.getZipRecord(zip_alias);

        if(zip_file==null) {
          showZips(zip_not_found_msg,req,res);
          return;
        }

        zip=zip_file.getZipFile();

        showEntireZip(zip,zip_file,req,res);
        zip_file.release(zip);
        return;
      }
    }

    if(path_info == null) {
      showZips(null,req,res);
      return;
    }

    zip_alias = getRequestedAlias(path_info);

    if(zip_alias == null) {
      showZips(null,req,res);
      return;
    }

    // The first char on PathInfo will always be a '/'

    if(path_info.length()==0 || path_info.length()==1) {
      showZips(null,req,res);
      return;
    }

    ZipRecord zfd = zipRepository.getZipRecord(getRequestedAlias(path_info));
    zip = zfd.getZipFile();
    if(zip == null) {
      zfd.valid = false;
      cantOpenZip(getRequestedAlias(path_info),req,res);
      return;
    }
    zip_file = null;

    //***************** From this point forward, in case of error
    //***************** a zfd.release(zip) must be performed.

    zipped_filename=path_info.substring(zip_alias.length()+1);

    if(zipped_filename==null || zipped_filename.length()==0 ||
     zipped_filename.equals("/")) {

      if(browsing)
        showDir(zip,"",req,res);
      else 
        redirectToStartPage(zfd.virtualDir,zfd.defaultEntryPoint,req,res);

      zfd.release(zip);
      return;
    }

    if(zipped_filename.charAt(0)=='/')
        zipped_filename=zipped_filename.substring(1);

    zip_entry=zip.getEntry(zipped_filename);

    if(zip_entry==null) {
      // First do some strange checks for strange files
      zip_entry=zip.getEntry("/"+zipped_filename);

      if(zip_entry==null) {
        // Then some stranger checks
        zip_entry=zip.getEntry("./"+zipped_filename);

        if(zip_entry==null) {
          // Then give up.
          fileNotFound(req,res);
          zfd.release(zip);
          return;
        }
      }
    }

    if(zip_entry.isDirectory()) {
      showDir(zip,zipped_filename,req,res);
      zfd.release(zip);
      return;
    }

    zipped_file=zip.getInputStream(zip_entry);

    if(zipped_file==null) {
      cantOpenZip(zip_alias,req,res);
      zfd.release(zip);
      return;
    }

    if(render == RENDER_TEXT)
      res.setContentType("text/plain");
    else if(render == RENDER_HEX)
      res.setContentType("text/html");
    else
      guessContentType(zipped_filename,res);

    try {
      if(render == RENDER_HEX)
        sendHexFile(zip_entry,zipped_file,out,req); // X-File ? :)
      else
        sendFile(zip_entry,zipped_file,out);
    } catch (IOException ioe) {
      zfd.release(zip);
      throw ioe;
    } catch (ServletException se) {
      zfd.release(zip);
      throw se;
    }

    zfd.release(zip);
    out.flush();
    out.close();
  }

  static void appendServletURL(StringBuilder sb, HttpServletRequest req) {
    String protocol = req.getProtocol();
    int port = req.getServerPort();

    sb.append("http");
    if(req.isSecure())
      sb.append('s');
    sb.append("://")
      .append(req.getServerName());
    if((!req.isSecure() && port != 80) ||
       (req.isSecure() && port != 443))
       sb.append(':').append(port);
    sb.append(req.getContextPath())
      .append(req.getServletPath());
  }

  /**
   * Return a string with the stylesheet to use on generated files
   */
  static String getStyleSheet(HttpServletRequest req) {
    StringBuilder sb = new StringBuilder();

    sb.append("<LINK REL=STYLESHEET TYPE=\"text/css\" HREF=\"");

    appendServletURL(sb, req);

    sb.append("/.resource/styles/style.css\">");

    return sb.toString();
  }

  /**
   * Shows the list of the served zip files
   */

  public void showZips(String msg,
                       HttpServletRequest req,
                       HttpServletResponse res)
    throws ServletException, IOException {

    ServletOutputStream out = res.getOutputStream();

    res.setContentType("text/html");

    boolean browsing = (req.getSession(true).getAttribute("browsemode") != null);

    out.println("<html>");
    out.println("<head>");
    out.println("<TITLE>ZipView Servlet</TITLE>");
    out.println(getStyleSheet(req));
    out.println("</head>");
    out.println("<body>");
    out.println("<span class=\"title\">ZipView Servlet</span><br>");
    if(msg!=null)
      out.println("<span class=\"subtitle\">"+msg+"</span>");
    out.println("<hr>");

    out.println("<center>");
    out.println("<table width=80%><tr><td>");

    out.println("<table border=0 cellspacing=0 cellpadding=1 width=100% class=\"outerbox\">");
    out.println("<tr><td class=\"tableheader\">Available zips</td></tr>");
    out.println("<tr><td><table border=0 cellspacing=0 cellpadding=3 width=100% class=\"innerbox\">");

    showZipsNew(out,req,res);

    out.println("</table></td></tr>");

    out.println("</table><br>");

    out.println("<table width=100%><tr>");
    out.println("<td width=\"33%\" align=center class=\"specialcell\">");

    out.println("</td>");
    out.println("<td width=\"33%\"></td>");
    out.println("<td width=\"33%\" align=center class=\"specialcell\">");
    if(browsing) {
      out.println("<a href=\"" + req.getServletPath() +
                  "/?nobrowse\">Disable browse mode");
    } else {
      out.println("<a href=\"" + req.getServletPath() +
                  "/?browse\">Enable browse mode");
    }
    out.println("</a></td>");

    out.println("</tr><tr>");
    out.println("<td colspan=2></td>");
    out.println("<td align=center class=\"specialcell\">");
    out.println("<a href=\"javascript:sidebar.addPanel('Zip View','"+getFullServletPath(req)+"?sidebar','');\">Add to Sidebar</a>");
    out.println("</td>");

    out.println("</tr><tr>");

    out.println("</tr></table>");

    out.println("</td></tr><tr><td>");

    out.println("</td></tr></table>");
    out.println("</center>");

    out.println("<hr>");

    out.println("<span class=\"notes\"><b>ZipView V. 2.0</b><ul>");
    out.println("<li>Append \"?text\" to any zipped file URL to see it as plain text.</li>");
    out.println("<li>Append \"?hex\" to any zipped file URL see an hex dump of it.</li></ul></span>");

    out.println("</body>");
    out.println("</html>");
  }
 
  /**
   * Show zips - new style
   */
  public void showZipsNew(ServletOutputStream out,
                          HttpServletRequest req,
                          HttpServletResponse res)
    throws ServletException, IOException {

    boolean browsing = (req.getSession(true).getAttribute("browsemode") != null);

    ZipRecord[] m_zips = zipRepository.getZips();
    
    for(int i=0;i<m_zips.length;i++) {
      ZipRecord za=m_zips[i];
      if(za == null)
        continue;

      out.println("<tr>");
      synchronized(za) {
        out.println("<td class=\"data\">");
        if(za.valid) {
          out.print("<a href=\""+req.getServletPath()+"/"+za.virtualDir+"/\">");
          out.print(za.virtualDir);
          out.print("</a>");
        } else {
          out.println(za.virtualDir);
        }
        out.print("</td>");

        out.println("<td class=\"desc\">");
        out.print("<a href=\""+req.getServletPath()+"/"+za.virtualDir+"/\">");
        out.print(za.description != null ? za.description : "<em>No description</em>");
        out.print("</a>");
        out.print("</td>");

        if(za.valid)
          out.println("<td class=\"data\"><span style=\"color:#00ff00\">OK</span></td>");
        else
          out.println("<td class=\"data\"><span style=\"color:#ff0000\">Invalid</span></td>");
      }
      out.println("</tr>");

      // Aid GC
      m_zips[i] = null;
    }
  }
 
  /**
   * Shows a mozilla sidebar panel
   */
  public void sidebar(HttpServletRequest req,
                      HttpServletResponse res)
    throws ServletException, IOException {

    ServletOutputStream out = res.getOutputStream();

    res.setContentType("text/html");

    out.println("<html>");
    out.println("<head>");
    out.println("<TITLE>ZipView Servlet</TITLE>");
    out.println(getStyleSheet(req));
    out.println("</head>");
    out.println("<body style=\"margin: 0px;\">");
    out.println("<center>");
    out.println("<table width=100% class=\"desc\">");

    ZipRecord[] m_zips = zipRepository.getZips();
    
    for(int i=0;i<m_zips.length;i++) {
      ZipRecord za=m_zips[i];
      if(za == null)
        continue;
  
      out.println("<tr>");
      synchronized(za) {
        out.println("<td class=\"desc\">");
        out.print("<a href=\"#\" onclick=\"window._content.location='"+getFullServletPath(req)+"/"+za.virtualDir+"/'\">");
        out.print(za.description != null ? za.description : "<em>No description</em>");
        out.print("</a>");
        out.print("</td>");
      }
      out.println("</tr>");
  
      // Aid GC
      m_zips[i] = null;
    }

    out.println("</table>");
    out.println("</center>");
    out.println("</body>");
    out.println("</html>");
  }
 
  /**
   * Redirects a "zip alias" path to a "zip alias/start page" path.
   *
   * @param za  zip alias
   * @param ap  starting page
   * @param req current http request
   * @param res current http response
   */
  public void redirectToStartPage(String za,
                                  String sp,
                                  HttpServletRequest req,
                                  HttpServletResponse res)
    throws ServletException, IOException {

    StringBuffer new_page = getFullServletPath(req);

    new_page.append('/').append(za).append('/');

    if(sp != null)
      new_page.append(sp);

    res.sendRedirect(new_page.toString());
  }

  /**
   * Shows a "File not found" error message.
   */
  public void fileNotFound(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {

    ServletOutputStream out=null;
    out = res.getOutputStream();

    res.setContentType("text/html");

    out.println("<html>");
    out.println("<head>");
    out.println("<TITLE>ZipView Servlet</TITLE>");
    out.println(getStyleSheet(req));
    out.println("</head>");
    out.println("<body>");
    out.println("<span class=\"title\">ZipView Servlet</span><br>");
    out.println("<hr>");
    out.println("<h2>Requested file not found </h2>");
    out.println("</body>");
    out.println("</html>");
  }

  /**
   * Shows a directory listening.
   */
  public void showDir(ZipFile zip,
                      String dir,
                      HttpServletRequest req,
                      HttpServletResponse res)
    throws ServletException, IOException {

    ServletOutputStream out = res.getOutputStream();
    int l=dir.length();

    res.setContentType("text/html");

    out.println("<html>");
    out.println("<head>");
    out.println("<TITLE>ZipView Servlet</TITLE>");
    out.println(getStyleSheet(req));
    out.println("</head>");
    out.println("<body>");
    out.println("<span class=\"title\">ZipView Servlet</span><br>");
    out.println("<span class=\"subtitle\">Contents for directory <b>/"+dir+"</b>:</span>");
    out.println("<hr>");
    out.println("<div class=\"direntry\">");
    out.println("[&nbsp;<a href=\"../\">&nbsp;Up one level&nbsp;</a>&nbsp;]<br>");

    for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();) {
      String entry=e.nextElement().getName();
      if(l==0 || entry.startsWith(dir)) {
        int p=entry.indexOf('/',l);

        if(p==-1 || (entry.length()==p+1 && entry.indexOf('/',p+1)==-1)) {
          entry=entry.substring(l);

          if(entry.length()>0)
            out.println("<a href=\""+entry+"\">"+entry+"</a><br>");
        }
      }
    }
    out.println("</div>");
    out.println("</body>");
    out.println("</html>");
  }

  /**
   * Shows the entire zip contents
   */
  public void showEntireZip(ZipFile zip,
                            ZipRecord zipi,
                            HttpServletRequest req,
                            HttpServletResponse res)
    throws ServletException, IOException {

    ServletOutputStream out = res.getOutputStream();

    res.setContentType("text/html");

    out.println("<html>");
    out.println("<head>");
    out.println("<TITLE>ZipView Servlet</TITLE>");
    out.println(getStyleSheet(req));
    out.println("</head>");
    out.println("<body>");
    out.println("<span class=\"title\">ZipView Servlet</span><br>");
    out.println("<span class=\"subtitle\">Entire zip contents:</span>");
    out.println("<hr>");
    out.println("<div class=\"direntry\">");

    for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();) {
      String entry=e.nextElement().getName();

      if(entry.length()>0)
        out.println("<a href=\"" + req.getServletPath() + "/" +
                    zipi.virtualDir + "/" + entry + "\">" + entry + "</a><br>");
    }
    out.println("</div>");
    out.println("</body>");
    out.println("</html>");
  }

  /**
   * Shown when a zip file is not found or when a file inside a zip
   * file is not found
   */
  public void cantOpenZip(String zip_alias,
                          HttpServletRequest req,
                          HttpServletResponse res)
    throws ServletException, IOException {

    StringBuffer new_page=req.getRequestURL();

    res.sendRedirect(
      new_page.toString().substring(0,new_page.toString().indexOf(zip_alias)));
  }

  /**
   * Sends a file to the client
   */
  public void sendFile(ZipEntry zip_entry,
                       InputStream zipped_file,OutputStream out)
    throws ServletException, IOException {

    long size;

    size=zip_entry.getSize();
    
    for(long i=0;i<size;i++)
      out.write(zipped_file.read());
  }

  /**
   * Sends a file to the client in Hex format
   */
  public void sendHexFile(ZipEntry zip_entry,
                          InputStream zipped_file,
                          ServletOutputStream out,
                          HttpServletRequest req)
    throws ServletException, IOException {

    long  size,i;
    int   j,b;
    int[] buffer = new int[16];

    out.println("<html>");
    out.println("<head>");
    out.println("<TITLE>ZipView Servlet</TITLE>");
    out.println(getStyleSheet(req));
    out.println("</head>");
    out.println("<body>");

    out.println("<font face=\"monospace\" size=-1>");

    size=zip_entry.getSize();
    
    for(i=0;i<size;) {
      buffer[(int) (i & 0xf)] = b = zipped_file.read();
      out.print(hex_values[(b >> 4) & 0xf]);
      out.print(hex_values[b & 0xf]);
      out.print(' ');
      i++;
      if((i & 0xf) == 0) {
        for(j=0;j<16;j++) {
          if(buffer[j] < 32)
            out.print(control_char);
          else if(buffer[j] == 32)
            out.print("&nbsp;");
          else {
            out.print("&#");
            out.print(buffer[j]);
            out.print(';');
          }
        }
        out.println("<br>");
      }
    }

    if((i & 0xf) != 0) {
      for(j=(int) (i & 0xf);j<16;j++)
        out.print("&nbsp;&nbsp;&nbsp;");

      for(j=0;j<(int) (i & 0xf);j++) {
        if(buffer[j]<32)
          out.print(control_char);
        else if(buffer[j] == 32)
          out.print("&nbsp;");
        else {
          out.print("&#");
          out.print(buffer[j]);
          out.print(';');
        }
      }
      out.println("<br>");
    }

    out.println("</font>");

    out.println("</body>");
    out.println("</html>");
  }

  /**
   * Returns the servlet info string
   */
  @Override
  public String getServletInfo() {
    return "The fantastic zip browser servlet 2.0";
  }
}
