import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class main {

    private static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Job> waitingJobs = new LinkedList<>();


    public static void main(String[] args) {

        InputData inputData = readFile("datasets/TOY-20-10.json");

        calculateInitialSolution(inputData);

        System.out.println("scheduled: \n"+scheduledTasks);
        System.out.println("waiting: \n"+waitingJobs);

    }




    public static void calculateInitialSolution(InputData inputData) {
        List<Job> jobs = inputData.getJobsSortedReleaseDate();

        // First job can always be added
        Job firstJob = jobs.get(0);
        firstJob.setStartDate(firstJob.getReleaseDate());
        planJob(firstJob, inputData);

        //TODO wat als eerste job niet gepland wordt: dan hieronder probleem bij het nemen van jobs.get(0)

        // Add to schedule if the job can be completed before dueDate
        for (int i = 1; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            // startDate is always bigger or equal to releaseDate
            //TODO startDate zit nog niet juist, want moet nog rekening houden met setup
            int startDate = Math.max(jobs.get(i-1).getFinishDate()+1, job.getReleaseDate());
            if(startDate+job.getDuration() <= job.getDueDate()) {
                job.setStartDate(startDate);
                planJob(job, inputData);
            }
            else {
                queueJob(job);
            }

        }
    }

    //TODO mogelijk maken om in te voegen, nu alleen maar achteraan toevoegen
    //TODO opsplitsen in planSetup en planJob, op een manier zorgen dat ze altijd samen worden uitgevoerd
    //  - kan niet in 1 blok, want er kan wel unavailable tussen setup en job
    //  - daarmee zal slechte if else hieronder al een pak beter zijn
    public static void planJob(Job job, InputData inputData) {
        job.calculateFinishDate();

        // Schedule setup
        if (!scheduledTasks.isEmpty()) { // no setup for first job
            assert scheduledTasks.getLast().getClass()==Job.class : "Last planned task is not a job";
            Job previousJob = (Job) scheduledTasks.getLast();
            Setup setup = inputData.getSetup(previousJob.getId(), job.getId());
            setup.setStartDate(previousJob.getFinishDate()+1);
            setup.calculateFinishDate();
            if (setup.checkExecutable(inputData.getUnavailablePeriods())) {
                scheduledTasks.add(setup);

                // Schedule job
                if (job.checkExecutable(inputData.getUnavailablePeriods())) {
                    scheduledTasks.add(job);
                }
                //TODO Reverse process if something fails
            }

            else {
                //TODO mss eens kijken om op efficiënte manier na de unavailable period te plannen i.p.v. direct in queue te stoppen
                queueJob(job);
            }
        }
        else {
            if (job.checkExecutable(inputData.getUnavailablePeriods())) {
                scheduledTasks.add(job);
            } else {
                //TODO mss eens kijken om op efficiënte manier na de unavailable period te plannen i.p.v. direct in queue te stoppen
                queueJob(job);
            }
        }

    }



    public static void queueJob(Job job) {
        job.setStartDate(-1);
        job.setFinishDate(-1);
        waitingJobs.add(job);
    }







    public static InputData readFile(String path) {
        InputData inputData = null;
        try {
            String jsonString = Files.readString(Paths.get(path));
            Gson gson = new Gson();
            inputData = gson.fromJson(jsonString, InputData.class);
//            System.out.println(inputData);
//            int[][] matrix = inputData.getSetupMatrix();
//            printMatrix(matrix);
        }
        catch (IOException e) {e.printStackTrace();}
        return inputData;
    }

    public static void printMatrix(int[][] matrix){
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }


}


