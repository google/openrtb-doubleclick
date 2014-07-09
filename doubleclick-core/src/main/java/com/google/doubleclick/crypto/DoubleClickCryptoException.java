/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.google.doubleclick.crypto;

import javax.annotation.Nullable;

/**
 * An Exception thrown by {@link DoubleClickCrypto} (or its nested classes)
 * if some encryption or decryption operation fails.
 */
public class DoubleClickCryptoException extends RuntimeException {
  public DoubleClickCryptoException(@Nullable String message) {
    super(message);
  }

  public DoubleClickCryptoException(@Nullable Throwable cause) {
    super(cause);
  }
}
