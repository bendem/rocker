/*
 * Copyright 2015 Fizzed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fizzed.rocker.runtime;

import com.fizzed.rocker.ContentType;
import com.fizzed.rocker.RockerOutputFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Output stores a list of references to byte arrays.  Extremely optimized for
 * taking static byte arrays (e.g. plain text) and just saving a pointer to it
 * rather than copying it. Optimized for writing bytes vs. Strings. Strings are
 * converted to bytes using the specified charset on each write and then that
 * byte array is internally stored as part of the underlying list. Thus, the
 * output will consist of reused byte arrays as well as new ones when Strings
 * are written to this output.
 *
 * @author joelauer
 */
public class ArrayOfByteArraysOutput extends AbstractRockerOutput<ArrayOfByteArraysOutput> {

    public static RockerOutputFactory<ArrayOfByteArraysOutput> FACTORY
        = new RockerOutputFactory<ArrayOfByteArraysOutput>() {
            @Override
            public ArrayOfByteArraysOutput create(ContentType contentType, String charsetName) {
                return new ArrayOfByteArraysOutput(contentType, charsetName);
            }
        };

    private final List<byte[]> arrays;

    public ArrayOfByteArraysOutput(ContentType contentType, String charsetName) {
        super(contentType, charsetName, 0);
        this.arrays = new ArrayList<byte[]>();
    }

    public ArrayOfByteArraysOutput(ContentType contentType, Charset charset) {
        super(contentType, charset, 0);
        this.arrays = new ArrayList<byte[]>();
    }

    public List<byte[]> getArrays() {
        return arrays;
    }

    @Override
    public ArrayOfByteArraysOutput w(String string) throws IOException {
        byte[] bytes = string.getBytes(charset);
        arrays.add(bytes);
        this.byteLength += bytes.length;
        return this;
    }

    @Override
    public ArrayOfByteArraysOutput w(byte[] bytes) throws IOException {
        arrays.add(bytes);
        this.byteLength += bytes.length;
        return this;
    }

    /**
     * Expensive operation of allocating a byte array to hold the entire contents
     * of this output and then copying each underlying byte array into this new
     * byte array.  Lots of memory copying...
     *
     * @return A new byte array
     */
    public byte[] toByteArray() {
        byte[] bytes = new byte[this.byteLength];
        int offset = 0;
        for (byte[] chunk : arrays) {
            System.arraycopy(chunk, 0, bytes, offset, chunk.length);
            offset += chunk.length;
        }
        return bytes;
    }

    @Override
    public String toString() {
        // super inneffecient method to convert to string
        // since byte arrays are not guaranteed to end with a complete char
        // (e.g. a unicode char that requireds multiple bytes) -- we need to
        // construct the entire array first before doing final convert to string
        byte[] bytes = toByteArray();
        return new String(bytes, this.charset);
    }

    public ReadableByteChannel asReadableByteChannel() {
        return new ReadableByteChannel() {

            private boolean closed = false;
            private int offset = 0;
            private final int length = getByteLength();
            private int chunkIndex = 0;
            private int chunkOffset = 0;

            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (closed) {
                    throw new ClosedChannelException();
                }

                // end of stream?
                if (arrays.isEmpty() || offset >= length) {
                    return -1;
                }

                int readBytes = 0;

                // keep trying to fill up buffer while it has capacity and we
                // still have data to fill it up with
                byte[] chunk = arrays.get(chunkIndex);
                int bufferSpace = dst.remaining();

                while (bufferSpace > 0 && (offset < length)) {
                    int chunkLength = chunk.length - chunkOffset;
                    int bytesToWrite = chunkLength;

                    if (chunkLength > bufferSpace) {
                        bytesToWrite = bufferSpace;
                    }

                    dst.put(chunk, chunkOffset, bytesToWrite);


                    offset += bytesToWrite;
                    bufferSpace -= bytesToWrite;
                    chunkOffset += bytesToWrite;
                    readBytes += bytesToWrite;

                    if (chunkOffset > chunkLength) {
                        if (chunkIndex + 1 == arrays.size()) {
                            break;
                        }
                        chunk = arrays.get(++chunkIndex);
                        chunkOffset = 0;
                    }
                }

                return readBytes;
            }

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() throws IOException {
                closed = true;
            }
        };
    }

    public InputStream asInputStream() {
        return new ByteArrayInputStream(toByteArray());
    }

    public InputStream asInputStream_New() {
        return Channels.newInputStream(asReadableByteChannel());
    }

    public InputStream asInputStream_Custom() {
        return new InputStream() {
            private int chunkIndex = 0;
            private int chunkOffset = 0;

            @Override
            public int read() {
                if (chunkIndex >= arrays.size()) {
                    return -1;
                }

                byte[] chunk = arrays.get(chunkIndex);
                byte b = chunk[chunkOffset];

                ++chunkOffset;

                if (chunkOffset >= chunk.length) {
                    chunkOffset = 0;
                    ++chunkIndex;
                }

                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (chunkIndex >= arrays.size()) {
                    return -1;
                }

                int readBytes = 0;

                byte[] chunk = arrays.get(chunkIndex);
                int bufferSpace = len - off;

                while (bufferSpace > 0 && (chunkIndex < arrays.size())) {
                    int chunkLength = chunk.length - chunkOffset;
                    int bytesToWrite = chunkLength;

                    if (chunkLength > bufferSpace) {
                        bytesToWrite = bufferSpace;
                    }

                    System.arraycopy(chunk, chunkOffset, b, off, bytesToWrite);

                    off += bytesToWrite;
                    bufferSpace -= bytesToWrite;
                    chunkOffset += bytesToWrite;
                    readBytes += bytesToWrite;

                    if (chunkOffset > chunkLength) {
                        if (chunkIndex + 1 == arrays.size()) {
                            break;
                        }
                        chunk = arrays.get(++chunkIndex);
                        chunkOffset = 0;
                    }
                }

                return readBytes;
            }
        };
    }
}
