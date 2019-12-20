package org.urajio.freshpastry.org.mpisws.p2p.transport.util;

import java.util.HashMap;
import java.util.Map;

public class OptionsFactory {
  public static Map<String, Object> addOption(Map<String, Object> existing, String s, Object i) {
    Map<String, Object> ret = copyOptions(existing);
    ret.put(s,i);
    return ret;
  }
  public static Map<String, Object> addOption(Map<String, Object> existing, String s1, Object i1, String s2, Object i2) {
    Map<String, Object> ret = copyOptions(existing);
    ret.put(s1,i1);
    ret.put(s2,i2);
    return ret;
  }
  
  public static Map<String, Object> addOption(Map<String, Object> existing, String s1, Object i1, String s2, Object i2, String s3, Object i3) {
    Map<String, Object> ret = copyOptions(existing);
    ret.put(s1,i1);
    ret.put(s2,i2);
    ret.put(s3,i3);
    return ret;
  }

  public static Map<String, Object> copyOptions(Map<String, Object> existing) {
    if (existing == null) return new HashMap<>();
    return new HashMap<>(existing);
  }
  
  /**
   * Merge 2 options, keeping the first if there is a conflict
   * 
   * @param options
   * @param options2
   * @return
   */
  public static Map<String, Object> merge(Map<String, Object> options,
      Map<String, Object> options2) {
    Map<String, Object> ret = copyOptions(options2);
    if (options == null) return ret;
    for (String k : options.keySet()) {
      ret.put(k, options.get(k));
    }
    return ret;
  }
  
  public static Map<String, Object> removeOption(Map<String, Object> options,
      String option) {
    Map<String, Object> ret = copyOptions(options);
    ret.remove(option);
    return ret;
  }
}
