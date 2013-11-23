/**
 * Implements a pool of resources
 */
public class ResourcePool {
  /**
   * The actual pool of resources
   */
  private Object[] pool;

  /**
   * Status of each pool entry
   */
  private int[] poolStatus;

  /**
   * Unused state
   */
  private static final int UNUSED      = 0;

  /**
   * The object exists but is not being used externally
   */
  private static final int INITIALIZED = 1;

  /**
   * The object is being used externally
   */
  private static final int ALLOCATED   = 2;

  /**
   * The used size of the pool.
   * Number of INITIALIZED + ALLOCATED
   */
  private int usedSize;

  /**
   * Free entries on the pool.
   * Number of INITIALIZED
   */
  private int freeEntries;

  /**
   * External object responsible for creating resources
   */
  private ResourceCreator creator;

  /**
   * Instantiate the resource pool
   */
  public ResourcePool(int max_size,ResourceCreator creator) {
    pool = new Object[max_size];
    poolStatus = new int[max_size];
    usedSize = 0;
    freeEntries = 0;

    this.creator = creator;

    for(int i=0;i<max_size;i++)
      poolStatus[i] = UNUSED;
  }

  /**
   * Get one element from the pool
   */
  public Object getResource() {
    int i;

    synchronized(pool) {
      while(true) {
        if(freeEntries > 0) {
          for(i=0;i<pool.length && poolStatus[i]!=INITIALIZED;i++) ;
          poolStatus[i] = ALLOCATED;
          freeEntries--;
          //System.out.println("allocate:"+pool.length+"/"+usedSize+"/"+freeEntries);
          return pool[i];
        } else if(usedSize < pool.length) {
          for(i=0;i<pool.length && poolStatus[i]!=UNUSED;i++) ;
          pool[i] = creator.createResource();
          usedSize++;
          poolStatus[i] = ALLOCATED;
          //System.out.println("create:"+pool.length+"/"+usedSize+"/"+freeEntries);
          return pool[i];
        } else {
          try {
            pool.wait();
          } catch(InterruptedException e) {
          }
        }
      }
    }
  }

    /**
     * Release one element to the pool
     */
    public void freeResource(Object resource) {
        int i;

        synchronized(pool) {
            for(i=0;i<pool.length && pool[i]!=resource;i++) ;
            poolStatus[i] = INITIALIZED;
            freeEntries++;
            //System.out.println("release:"+pool.length+"/"+usedSize+"/"+freeEntries);
            pool.notify();
        }
    }

    /**
     * Flush the pool
     */
    public void flushResources() {
        int i;

        synchronized(pool) {
            //System.out.println("flush:"+pool.length+"/"+usedSize+"/"+freeEntries);
            for(i=0;i<pool.length;i++) {
                if(poolStatus[i] == INITIALIZED) {
                    creator.destroyResource(pool[i]);
                    pool[i] = null;
                    poolStatus[i] = UNUSED;
                    freeEntries--;
                    usedSize--;
                }
            }
            pool.notify();
        }
    }
}

