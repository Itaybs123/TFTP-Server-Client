package bgu.spl.net.impl.tftp;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentHashMap;

public class FileLockManager {
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> lockMap = new ConcurrentHashMap<>();

    private ReentrantReadWriteLock getLock(String filePath) {
        return lockMap.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());
    }

    public void lockRead(String filePath) {
        getLock(filePath).readLock().lock();
    }

    public void unlockRead(String filePath) {
        getLock(filePath).readLock().unlock();
    }

    public void lockWrite(String filePath) {
        getLock(filePath).writeLock().lock();
    }

    public void unlockWrite(String filePath) {
        getLock(filePath).writeLock().unlock();
    }
}
