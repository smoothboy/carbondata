/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.carbondata.core.cache;

import org.carbondata.core.cache.dictionary.Dictionary;
import org.carbondata.core.cache.dictionary.DictionaryColumnUniqueIdentifier;

/**
 * class which defines different cache types. cache type can be dictionary cache for
 * forward (surrogate key to byte array mapping) and reverse (byte array to
 * surrogate mapping) dictionary or a B-tree cache
 */
public class CacheType<K, V> {

  /**
   * Forward dictionary cache which maintains surrogate key to byte array mapping
   */
  public static final CacheType<DictionaryColumnUniqueIdentifier, Dictionary> FORWARD_DICTIONARY =
      new CacheType("forward_dictionary");

  /**
   * Reverse dictionary cache which maintains byte array to surrogate key mapping
   */
  public static final CacheType<DictionaryColumnUniqueIdentifier, Dictionary> REVERSE_DICTIONARY =
      new CacheType("reverse_dictionary");

  /**
   * cacheName which is unique name for a cache
   */
  private String cacheName;

  /**
   * @param cacheName
   */
  private CacheType(String cacheName) {
    this.cacheName = cacheName;
  }

  /**
   * @return cache unique name
   */
  public String getCacheName() {
    return cacheName;
  }
}
