import java.util.*;

public interface ResourceCreator {
  public Object createResource();
  public void destroyResource(Object resource);
}
