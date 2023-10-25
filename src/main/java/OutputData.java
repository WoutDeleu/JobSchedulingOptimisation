import com.google.gson.annotations.Expose;

import java.util.List;

public class OutputData {
    @Expose
    private String name;
    @Expose
    private double value;
    @Expose
    private List<Job> jobs;
    @Expose
    private List<Setup> setups;

    public OutputData(String name, double value, List<Job> jobs, List<Setup> setups) {
        this.name = name;
        this.value = value;
        this.jobs = jobs;
        this.setups = setups;
    }
}
