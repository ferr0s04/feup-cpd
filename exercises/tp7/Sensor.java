import java.net.*;
import java.io.*;
import java.util.*;

class Sensor {
    private int id;
    private List<Float> readings;

    public Sensor(int id) {
        this.id = id;
        this.readings = new ArrayList<>();
    }

    public synchronized void addReading(float value) {
        readings.add(value);
    }

    public synchronized float getAverage() {
        if (readings.isEmpty()) return 0;
        float sum = 0;
        for (float val : readings) {
            sum += val;
        }
        return sum / readings.size();
    }
}
