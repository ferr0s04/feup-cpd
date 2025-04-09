import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

class Sensor {
    private final int id;
    private final List<Float> readings;
    private final ReentrantLock lock;

    public Sensor(int id) {
        this.id = id;
        this.readings = new ArrayList<>();
        this.lock = new ReentrantLock();
    }

    public void addReading(float value) {
        lock.lock();
        try {
            readings.add(value);
        } finally {
            lock.unlock();
        }
    }

    public float getAverage() {
        lock.lock();
        try {
            if (readings.isEmpty()) return 0;
            float sum = 0;
            for (float val : readings) {
                sum += val;
            }
            return sum / readings.size();
        } finally {
            lock.unlock();
        }
    }
}
