/**
 * Ponto de entrada para um ficheiro Zip
 */
public class EntryPoint {
  /**
   * URL relativo (interno ao zip).
   */
  public String url;

  /**
   * Descricao do documento apontado pelo URL
   */
  public String description;

  /**
   * Metodo standard
   */
  public String toString() {
    return url + ":" + description;
  }
}

