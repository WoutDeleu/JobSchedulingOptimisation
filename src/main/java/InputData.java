import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;

public class InputData {
    private String name;
    @SerializedName("weight_duration")
    private double weightDuration;
    private int horizon;
    private ArrayList<Job> jobs = new ArrayList<>();
    private ArrayList<UnavailablePeriod> unavailability = new ArrayList<>();
    private ArrayList<int[]> setups = new ArrayList<>();

    public int[][] getSetupMatrix() {
        int N = jobs.size();
        int[][] matrix = new int[N][N];
        for (int i = 0; i < N; i++)
            matrix[i] = Arrays.copyOf(setups.get(i), N);

        return matrix;
    }

    @Override
    public String toString() {
        return "InputData{" +
                "name='" + name + '\'' +
                ", weightDuration=" + weightDuration +
                ", horizon=" + horizon +
                ",\n jobs=" + jobs +
                ", unavailability=" + unavailability +
//                ", setups=" + setups +
                '}';
    }
}
