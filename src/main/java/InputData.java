import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
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

    public static File[] getFiles(String directory) {
       File dir = new File(directory);
       return dir.listFiles();
    }

    public static InputData readFile(String path) {
        InputData inputData = null;
        try {
            String jsonString = Files.readString(Paths.get(path));
            Gson gson = new Gson();
            inputData = gson.fromJson(jsonString, InputData.class);
        }
        catch (IOException e) {e.printStackTrace();}
        return inputData;
    }

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

    public void printSetupMatrix() {
        System.out.println("Setup matrix: ");
        int[][] matrix = getSetupMatrix();
        for (int i = 0; i < matrix.length ; i++) {
            for (int j = 0; j < matrix.length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }

    public Setup getSetup(int id1, int id2) {
        int duration = setups.get(id1)[id2];
        return new Setup(duration, id1, id2);
    }

    public String getName() {return name;}

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

    public static void writeFile(String path, OutputData outputData) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
            String jsonString = gson.toJson(outputData);
            PrintWriter printer = new PrintWriter(new FileWriter(path));
            printer.write(jsonString);
            printer.close();
        }
        catch (IOException e) {e.printStackTrace();}
    }

    public static OutputData generateOutput(String name, double score, LinkedList<Task> scheduledTasks) {
        List<Job> jobs = new ArrayList<>();
        List<Setup> setups = new ArrayList<>();
        for (Task task : scheduledTasks) {
            if (task.getClass()==Job.class) jobs.add((Job) task);
            else if (task.getClass()==Setup.class) setups.add((Setup) task);
            else throw new IllegalStateException("Item found that was neither a job nor a setup");
        }
        return new OutputData(name, score, jobs, setups);
    }

}
