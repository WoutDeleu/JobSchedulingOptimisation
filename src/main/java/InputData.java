import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InputData {
    private String name;
    @SerializedName("weight_duration")
    private double weightDuration;

    public int getHorizon() {
        return horizon;
    }

    private int horizon;
    private ArrayList<Job> jobs = new ArrayList<>();
    @SerializedName("unavailability")
    private ArrayList<UnavailablePeriod> unavailablePeriods = new ArrayList<>();
    private ArrayList<int[]> setups = new ArrayList<>();


    public SetupList generateSetupList() {
        return new SetupList(setups);
    }

    public int[][] getSetupMatrix() {
        int N = jobs.size();
        int[][] matrix = new int[N][N];
        for (int i = 0; i < N; i++)
            matrix[i] = Arrays.copyOf(setups.get(i), N);

        return matrix;
    }

    public Setup getSetup(int id1, int id2) {
        int duration = setups.get(id1)[id2];
        return new Setup(duration, id1, id2);
    }

    public List<Job> getJobsSortedReleaseDate() {
        // sort by release date
        jobs.sort(new JobComparator());
        return jobs;
    }

    public double getWeightDuration() {
        return weightDuration;
    }

    public List<UnavailablePeriod> getUnavailablePeriods() {
        return unavailablePeriods;
    }

    @Override
    public String toString() {
        return "InputData{" +
                "name='" + name + '\'' +
                ", weightDuration=" + weightDuration +
                ", horizon=" + horizon +
                ",\n jobs=" + jobs +
                ", unavailability=" + unavailablePeriods +
//                ", setups=" + setups +
                '}';
    }

}
