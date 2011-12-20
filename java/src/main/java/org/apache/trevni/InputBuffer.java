/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.trevni;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/** */

class InputBuffer {
  private Input in;

  private long inLength;
  private long offset;                            // pos of next read from in

  private byte[] buf;                             // data from input
  private int pos;                                // position within buffer
  private int limit;                              // end of valid buffer data

  private int valueCount;

  public InputBuffer(Input in) throws IOException { this(in, 0); }

  public InputBuffer(Input in, long position) throws IOException {
    this.in = in;
    this.inLength = in.length();
    this.offset = position;

    if (in instanceof InputBytes) {               // use buffer directly
      this.buf = ((InputBytes)in).getBuffer();
      this.limit = (int)in.length();
      this.offset = limit;
    } else {                                      // create new buffer
      this.buf = new byte[8192];                  // big enough for primitives
    }
  }

  public void seek(long position) throws IOException {
    if (position >= (offset-limit) && position <= offset) {
      pos = (int)(limit - (offset - position));   // seek in buffer;
      return;
    }
    pos = 0;
    limit = 0;
    offset = position;
  }

  public long tell() { return (offset-limit)+pos; }

  public long length() { return inLength; }

  public int valueCount() { return valueCount; }
  public void valueCount(int valueCount) { this.valueCount = valueCount; }

  public Object readValue(ValueType type)
    throws IOException {
    valueCount++;
    switch (type) {
    case INT:
      return readInt();
    case LONG:
      return readLong();
    case FIXED32:
      return readFixed32();
    case FIXED64:
      return readFixed64();
    case FLOAT:
      return readFloat();
    case DOUBLE:
      return readDouble();
    case STRING:
      return readString();
    case BYTES:
      return readBytes();
    default:
      throw new TrevniRuntimeException("Unknown value type: "+type);
    }
  }

  public int readInt() throws IOException {
    if ((limit - pos) < 5) {                      // maybe not in buffer
      int b = read();
      int n = b & 0x7f;
      for (int shift = 7; b > 0x7f; shift += 7) {
        b = read();
        n ^= (b & 0x7f) << shift;
      }
      return (n >>> 1) ^ -(n & 1);                  // back to two's-complement
    }
    int len = 1;
    int b = buf[pos] & 0xff;
    int n = b & 0x7f;
    if (b > 0x7f) {
      b = buf[pos + len++] & 0xff;
      n ^= (b & 0x7f) << 7;
      if (b > 0x7f) {
        b = buf[pos + len++] & 0xff;
        n ^= (b & 0x7f) << 14;
        if (b > 0x7f) {
          b = buf[pos + len++] & 0xff;
          n ^= (b & 0x7f) << 21;
          if (b > 0x7f) {
            b = buf[pos + len++] & 0xff;
            n ^= (b & 0x7f) << 28;
            if (b > 0x7f) {
              throw new IOException("Invalid int encoding");
            }
          }
        }
      }
    }
    pos += len;
    if (pos > limit)
      throw new EOFException();
    return (n >>> 1) ^ -(n & 1);                  // back to two's-complement
  }

  public long readLong() throws IOException {
    if ((limit - pos) < 10) {                     // maybe not in buffer
      int b = read();
      long n = b & 0x7f;
      for (int shift = 7; b > 0x7f; shift += 7) {
        b = read();
        n ^= (b & 0x7fL) << shift;
      }
      return (n >>> 1) ^ -(n & 1);                // back to two's-complement
    }

    int b = buf[pos++] & 0xff;
    int n = b & 0x7f;
    long l;
    if (b > 0x7f) {
      b = buf[pos++] & 0xff;
      n ^= (b & 0x7f) << 7;
      if (b > 0x7f) {
        b = buf[pos++] & 0xff;
        n ^= (b & 0x7f) << 14;
        if (b > 0x7f) {
          b = buf[pos++] & 0xff;
          n ^= (b & 0x7f) << 21;
          if (b > 0x7f) {
            // only the low 28 bits can be set, so this won't carry
            // the sign bit to the long
            l = innerLongDecode((long)n);
          } else {
            l = n;
          }
        } else {
          l = n;
        }
      } else {
        l = n;
      }
    } else {
      l = n;
    }
    if (pos > limit) {
      throw new EOFException();
    }
    return (l >>> 1) ^ -(l & 1); // back to two's-complement
  }
  
  // splitting readLong up makes it faster because of the JVM does more
  // optimizations on small methods
  private long innerLongDecode(long l) throws IOException {
    int len = 1;
    int b = buf[pos] & 0xff;
    l ^= (b & 0x7fL) << 28;
    if (b > 0x7f) {
      b = buf[pos + len++] & 0xff;
      l ^= (b & 0x7fL) << 35;
      if (b > 0x7f) {
        b = buf[pos + len++] & 0xff;
        l ^= (b & 0x7fL) << 42;
        if (b > 0x7f) {
          b = buf[pos + len++] & 0xff;
          l ^= (b & 0x7fL) << 49;
          if (b > 0x7f) {
            b = buf[pos + len++] & 0xff;
            l ^= (b & 0x7fL) << 56;
            if (b > 0x7f) {
              b = buf[pos + len++] & 0xff;
              l ^= (b & 0x7fL) << 63;
              if (b > 0x7f) {
                throw new IOException("Invalid long encoding");
              }
            }
          }
        }
      }
    }
    pos += len;
    return l;
  }

  public float readFloat() throws IOException {
    return Float.intBitsToFloat(readFixed32());
  }

  public int readFixed32() throws IOException {
    if ((limit - pos) < 4)                        // maybe not in buffer
      return read() | (read() << 8) | (read() << 16) | (read() << 24);

    int len = 1;
    int n = (buf[pos] & 0xff) | ((buf[pos + len++] & 0xff) << 8)
        | ((buf[pos + len++] & 0xff) << 16) | ((buf[pos + len++] & 0xff) << 24);
    if ((pos + 4) > limit)
      throw new EOFException();
    pos += 4;
    return n;
  }

  public double readDouble() throws IOException {
    return Double.longBitsToDouble(readFixed64());
  }

  public long readFixed64() throws IOException {
    return (readFixed32() & 0xFFFFFFFFL) | (((long)readFixed32()) << 32);
  }

  private static final Charset UTF8 = Charset.forName("UTF-8");

  public String readString() throws IOException {
    int length = readInt();
    if (length <= (limit - pos)) {                        // in buffer
      String result = new String(buf, pos, length, UTF8); // read directly
      pos += length;
      return result;
    }
    byte[] bytes = new byte[length];
    readFully(bytes, 0, length);
    return new String(bytes, 0, length, UTF8);
  }  

  public byte[] readBytes() throws IOException {
    byte[] result = new byte[readInt()];
    readFully(result);
    return result;
  }

  public ByteBuffer readBytes(ByteBuffer old) throws IOException {
    int length = readInt();
    ByteBuffer result;
    if (old != null && length <= old.capacity()) {
      result = old;
      result.clear();
    } else {
      result = ByteBuffer.allocate(length);
    }
    readFully(result.array(), result.position(), length);
    result.limit(length);
    return result;
  }

  public void skipBytes() throws IOException {
    skip(readInt());
  }

  private void skip(long length) throws IOException {
    seek(tell()+length);
  }

  public int read() throws IOException {
    if (pos >= limit) {
      limit = readInput(buf, 0, buf.length);
      pos = 0;
    }
    return buf[pos++] & 0xFF;
  }

  public void readFully(byte[] bytes) throws IOException {
    readFully(bytes, 0, bytes.length);
  }

  public void readFully(byte[] bytes, int start, int len) throws IOException {
    int buffered = limit - pos;
    if (len > buffered) {                        // buffer is insufficient

      System.arraycopy(buf, pos, bytes, start, buffered); // consume buffer
      start += buffered;
      len -= buffered;
      pos += buffered;
      if (len > buf.length) {                     // bigger than buffer
        do {
          int read = readInput(bytes, start, len); // read directly into result
          len -= read;
          start += read;
        } while (len > 0);
        return;
      }

      limit = readInput(buf, 0, buf.length);        // refill buffer
      pos = 0;
    }

    System.arraycopy(buf, pos, bytes, start, len); // copy from buffer
    pos += len;
  }

  private int readInput(byte[] b, int start, int len) throws IOException {
    int read = in.read(offset, b, start, len);
    if (read < 0) throw new EOFException();
    offset += read;
    return read;
 }

}
