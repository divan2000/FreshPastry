package org.urajio.freshpastry.rice.environment.params;

/**
 *   Listener interface for changes to a parameters object
 *
 * @author jstewart
 *
 */
public interface ParameterChangeListener {
  void parameterChange(String paramName, String newVal);
}
