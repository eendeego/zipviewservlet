import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * A Zip file referenced in the system
 */
public class ZipRecord {
  /**
   * Virtual directory for accessing the Zip file
   */
  public String virtualDir;
 
  /**
   * Real file on the machine file system
   */
  public String file;
 
  /**
   * Zip file description
   */
  public String description;
 
  /**
   * Default accessed file inside the Zip
   */
  public String defaultEntryPoint;
 
  /**
   * Other URL's for documents in the Zip
   */
  public EntryPoint[] entryPoints;
 
  /**
   * This record corresponds to a valid zip file
   */
  public boolean valid = true;
 
  /**
   * Zip pool
   */
  private ResourcePool pool = null;
 
  /**
   * Return a ZipFile from the pool
   */
  public ZipFile getZipFile() {
    synchronized(this) {
      if(pool == null)
        pool = new ResourcePool(10,new ResourceCreator() {
          public Object createResource() {
            try {
              //System.out.println("Opening "+file);
              return new ZipFile(file);
            } catch(IOException e) {
              return null;
            }
          }

          public void destroyResource(Object resource) {
            try {
              //System.out.println("Closing "+((ZipFile) resource).getName());
              ((ZipFile) resource).close();
            } catch(IOException e) {
            }
          }
        });
    }
    
    return (ZipFile) pool.getResource();
  }

  /**
   * Release a ZipFile from the pool
   */
  public void release(ZipFile zip) {
    pool.freeResource(zip);
  }

  /**
   * Flush the zip pool
   */
  public void flushZips() {
    if(pool != null)
        pool.flushResources();
  }

  /**
   * Text representation of this object
   */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(virtualDir).append(":").append(file).append(":").
      append(description).append(":").append(defaultEntryPoint);

    if(entryPoints == null || entryPoints.length==0)
      return sb.append(":sem + entry points").toString();

    sb.append("{").append(entryPoints[0]);
    for(int i=1;i<entryPoints.length;i++)
      sb.append(",").append(entryPoints[i]);

    return sb.append("}").toString();
  }
}
