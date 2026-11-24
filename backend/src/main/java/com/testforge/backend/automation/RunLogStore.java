package com.testforge.backend.automation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunLogStore {

    public record Chunk(List<String> lines, long next) {
    }

    private static final int MAX_RUNS_KEPT = 50;

    private final AutomationProperties properties;
    private final RunEventPublisher publisher;

    private final Map<Long, Buffer> buffers = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Buffer> eldest) {
            return size() > MAX_RUNS_KEPT;
        }
    };

    public void append(Long runId, String line) {
        Buffer buffer;
        synchronized (buffers) {
            buffer = buffers.computeIfAbsent(runId, id -> new Buffer(properties.maxLogLines()));
        }
        long offset = buffer.add(line);
        publisher.log(runId, offset, line);
    }

    public Chunk read(Long runId, long from) {
        Buffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        return buffer == null ? new Chunk(List.of(), Math.max(from, 0)) : buffer.read(from);
    }

    private static final class Buffer {
        private final int capacity;
        private final Deque<String> lines = new ArrayDeque<>();
        private long firstOffset = 0;

        Buffer(int capacity) {
            this.capacity = capacity;
        }

        synchronized long add(String line) {
            long offset = firstOffset + lines.size();
            lines.addLast(line);
            while (lines.size() > capacity) {
                lines.removeFirst();
                firstOffset++;
            }
            return offset;
        }

        synchronized Chunk read(long from) {
            long start = Math.max(from, firstOffset);
            long skip = start - firstOffset;
            List<String> out = new ArrayList<>();
            long index = 0;
            for (String line : lines) {
                if (index++ >= skip) {
                    out.add(line);
                }
            }
            return new Chunk(out, firstOffset + lines.size());
        }
    }
}