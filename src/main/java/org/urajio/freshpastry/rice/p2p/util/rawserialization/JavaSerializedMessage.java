package org.urajio.freshpastry.rice.p2p.util.rawserialization;

import org.urajio.freshpastry.rice.p2p.commonapi.Message;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;
import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.RawMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Wrapper that converts rice.pastry.messaging.Message to rice.pastry.messageing.PRawMessage
 * 
 * @author Jeff Hoye
 */
public class JavaSerializedMessage implements RawMessage {

  Message msg;
  
//  Exception constructionStack;
  
  public JavaSerializedMessage(Message msg) {
    this.msg = msg;
    if (msg == null) throw new RuntimeException("msg cannot be null");
//    constructionStack = new Exception("Stack Trace: msg:"+msg).printStackTrace();
  }

  public short getType() {
    return 0;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
  
      // write out object and find its length
      oos.writeObject(msg);
      oos.close();
      
      byte[] temp = baos.toByteArray();
      buf.write(temp, 0, temp.length);
  //    System.out.println("JavaSerializedMessage.serailize() "+msg+" length:"+temp.length);
  //    new Exception("Stack Trace").printStackTrace();
  //    constructionStack.printStackTrace();
    } catch (IOException ioe) {
      throw new JavaSerializationException(msg, ioe);
    }
  }
  
  public Message getMessage() {
    return msg; 
  }

  public int getPriority() {
    return msg.getPriority();
  }
  
  public String toString() {
    return "JavaSerializedMessage["+msg+"]"; 
  }
}
