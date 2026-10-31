package tw.mediaorganizer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks MediaStore entries verified as deleted during the current app process.
 * Concurrent scans must never re-introduce an entry from an older snapshot.
 * Uses canonical content URIs as keys, never display names or file paths.
 */
final class DeletedMediaRegistry {
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();

    void record(String uri) {
        if (uri != null && !uri.isEmpty()) deleted.add(uri);
    }

    boolean contains(String uri) {
        return uri != null && deleted.contains(uri);
    }

    int size() {
        return deleted.size();
    }
}
