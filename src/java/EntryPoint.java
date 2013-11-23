/**
 * Default document in a zip file
 */
public class EntryPoint {
  /**
   * Relative URL (internal to the zip).
   */
  public String url;

  /**
   * Document's description
   */
  public String description;

  /**
   * String representation of the entry point
   */
  public String toString() {
    return url + ":" + description;
  }
}
