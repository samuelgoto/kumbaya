/**
 * Copyright 2010, 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kumbaya.dht;

import org.limewire.mojito.db.DHTValue;
import org.limewire.mojito.db.DHTValueEntity;
import org.limewire.mojito.db.DHTValueType;
import org.limewire.mojito.db.impl.DHTValueImpl;
import org.limewire.mojito.routing.Version;

import com.google.common.base.Preconditions;

public class Values {
  public static DHTValue of(String value) {
    return of(value, DHTValueType.TEXT);
  }

  public static DHTValue of(String value, DHTValueType type) {
    return new DHTValueImpl(type, Version.ZERO, value.getBytes());
  }
  
  public static DHTValue of(String value, String type) {
	  int hash = 7;
	  for (int i = 0; i < type.length(); i++) {
	      hash = hash * 31 + type.charAt(i);
	  }
	  return of(value, DHTValueType.valueOf(hash));
  }
  
  public static String of(DHTValueEntity entity) {
	  Preconditions.checkArgument(entity.getValue().getValueType() == DHTValueType.TEXT);
	  return new String(entity.getValue().getValue());
  }
}
