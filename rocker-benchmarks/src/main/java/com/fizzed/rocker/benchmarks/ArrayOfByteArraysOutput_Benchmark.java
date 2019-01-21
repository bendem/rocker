package com.fizzed.rocker.benchmarks;

import com.fizzed.rocker.runtime.ArrayOfByteArraysOutput;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import templates.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

@BenchmarkMode(Mode.Throughput)
public class ArrayOfByteArraysOutput_Benchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {

        @Param({"1", "16", "1024"})
        public static int bufferSize;

        public ArrayOfByteArraysOutput test = Test.template("hi", "bob")
            .render(ArrayOfByteArraysOutput::new);

        public ByteBuffer buffer;
        public byte[] bytes;

        @Setup
        public void setup() {
            buffer = ByteBuffer.allocate(bufferSize);
            bytes = new byte[bufferSize];
        }

    }

    @Benchmark
    public void test_asByteChannel(BenchmarkState state, Blackhole blackhole) throws IOException {
        try (ReadableByteChannel channel = state.test.asReadableByteChannel()) {
            while (channel.read(state.buffer) > 0) {
                blackhole.consume(state.buffer);
                state.buffer.clear();
            }
        }
    }

    @Benchmark
    public void test_asInputStream_ByteArrayInputStream(BenchmarkState state, Blackhole blackhole) throws IOException {
        try (InputStream stream = state.test.asInputStream()) {
            while (stream.read(state.bytes) > 0) {
                blackhole.consume(state.bytes);
            }
        }
    }

    @Benchmark
    public void test_asInputStream_ConvertByteChannelToInputStream(BenchmarkState state, Blackhole blackhole) throws IOException {
        try (InputStream stream = state.test.asInputStream_New()) {
            while (stream.read(state.bytes) > 0) {
                blackhole.consume(state.bytes);
            }
        }
    }

    @Benchmark
    public void test_asInputStream_CustomInputStream(BenchmarkState state, Blackhole blackhole) throws IOException {
        try (InputStream stream = state.test.asInputStream_Custom()) {
            while (stream.read(state.bytes) > 0) {
                blackhole.consume(state.bytes);
            }
        }
    }
}
