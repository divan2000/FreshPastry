/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
/*
 * Created on May 26, 2005
 */
package org.urajio.freshpastry.rice.environment.params;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Parameters interface for FreePastry
 * 
 * Usually acquired by calling environment.getParameters().
 * 
 * @author Jeff Hoye
 */
public interface Parameters {
  /**
   * Remove the key
   * @param name
   */
  void remove(String name);
  /**
   * See if the parameters contains the key
   * 
   * @param name
   * @return
   */
  boolean contains(String name);
  /**
   * Persistently stores the parameters.
   * @throws IOException
   */
  void store() throws IOException;
  
  // getters
  String getString(String paramName);
  String[] getStringArray(String paramName);
  int getInt(String paramName);
  double getDouble(String paramName);
  float getFloat(String paramName);
  long getLong(String paramName);
  boolean getBoolean(String paramName);
  
  /**
   * String format is dnsname
   * ex: "computer.school.edu"
   * @param paramName
   * @return
   * @throws UnknownHostException
   */
  InetAddress getInetAddress(String paramName) throws UnknownHostException;
  
  
  /**
   * String format is name:port
   * ex: "computer.school.edu:1984"
   * @param paramName
   * @return
   */
  InetSocketAddress getInetSocketAddress(String paramName) throws UnknownHostException;
  
  /**
   * String format is comma seperated.
   * ex: "computer.school.edu:1984,computer2.school.edu:1984,computer.school.edu:1985"
   * @param paramName
   * @return
   */
  InetSocketAddress[] getInetSocketAddressArray(String paramName) throws UnknownHostException;
  
  // setters
  void setString(String paramName, String val);
  void setStringArray(String paramName, String[] val);
  void setInt(String paramName, int val);
  void setDouble(String paramName, double val);
  void setFloat(String paramName, float val);
  void setLong(String paramName, long val);
  void setBoolean(String paramName, boolean val);
  void setInetAddress(String paramName, InetAddress val);
  void setInetSocketAddress(String paramName, InetSocketAddress val);
  void setInetSocketAddressArray(String paramName, InetSocketAddress[] val);
  void restoreDefault(String paramName);
    
  void addChangeListener(ParameterChangeListener p);
  void removeChangeListener(ParameterChangeListener p);
}
