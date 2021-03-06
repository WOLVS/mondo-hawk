/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.hawk.service.api;


import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

public enum CommitItemChangeType implements org.apache.thrift.TEnum {
  ADDED(0),
  DELETED(1),
  REPLACED(2),
  UNKNOWN(3),
  UPDATED(4);

  private final int value;

  private CommitItemChangeType(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static CommitItemChangeType findByValue(int value) { 
    switch (value) {
      case 0:
        return ADDED;
      case 1:
        return DELETED;
      case 2:
        return REPLACED;
      case 3:
        return UNKNOWN;
      case 4:
        return UPDATED;
      default:
        return null;
    }
  }
}
