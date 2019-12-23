package org.urajio.freshpastry.rice.environment.processing.simple;

import org.urajio.freshpastry.rice.environment.processing.WorkRequest;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Jeff Hoye
 */
@SuppressWarnings("unchecked")
public class WorkQueue {
  List<WorkRequest> q = new LinkedList<>();
  /* A negative capacity, is equivalent to infinted capacity */
  int capacity = -1;

  volatile boolean running = true;
  
  public WorkQueue() {
     /* do nothing */
  }
  
  public synchronized int getLength() {
    return q.size();
  }
  
  public WorkQueue(int capacity) {
     this.capacity = capacity;
  }
  
  public synchronized void enqueue(WorkRequest request) {
    if (capacity < 0 || q.size() < capacity) {
      q.add(request);
      notifyAll();
    } else {
      request.returnError(new WorkQueueOverflowException());
    }
  }
  
  public synchronized WorkRequest dequeue() {
    while (q.isEmpty() && running) {
      try {
        wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    
    if (!running) return null;
    return q.remove(0);
  }
  
  public void destroy() {
    running = false;
    synchronized(this) {
      notifyAll(); 
    }
  }

}
