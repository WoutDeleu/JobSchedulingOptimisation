import java.util.ArrayList;
import java.util.List;

public class OutputData {
    private String name;
    private double value;
    private List<Job> jobs;
    private List<Setup> setups;

    public OutputData(String name, double value, List<Job> jobs, List<Setup> setups) {
        this.name = name;
        this.value = value;
        this.jobs = jobs;
        this.setups = setups;
    }
}
