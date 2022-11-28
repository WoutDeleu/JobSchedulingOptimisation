import com.github.sh0nk.matplotlib4j.Plot;
import com.github.sh0nk.matplotlib4j.PythonExecutionException;

import java.io.IOException;
import java.util.ArrayList;

public class Plotter {
    /*
    To make it work: save the costs and time values for that cost in an arraylist, and give it to the plotter

        ArrayList<Long> times = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        while() {
            long currTime = System.currentTimeMillis() - startTime;
            times.add(currTime);
            values.add(value);
        }
     */
    public static void plotTimes(ArrayList<Long> times, ArrayList<Long> costs) throws PythonExecutionException, IOException {
        Plot plt  = Plot.create();
        plt.plot().add(times, costs);
        plt.xlabel("times (ms)");
        plt.ylabel("Costs");
        plt.title("Cost over time (ms)");
        plt.show();
    }
}
