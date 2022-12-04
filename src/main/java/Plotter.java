import com.github.sh0nk.matplotlib4j.Plot;
import com.github.sh0nk.matplotlib4j.PythonExecutionException;

import java.io.IOException;
import java.util.ArrayList;

public class Plotter {
    public static void plotTimes(ArrayList<Long> times, ArrayList<Long> costs, String name) throws PythonExecutionException, IOException {
        Plot plt  = Plot.create();
        plt.plot().add(times, costs);
        plt.xlabel("times (ms)");
        plt.ylabel("Costs");
        plt.title(name + " - Cost over time (ms)");
        plt.show();
    }
}
