import com.google.gson.Gson;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class main_Wout {

    private static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static int horizon;
    private static int currentBestValue;

    public static void main(String[] args) {
        InputData inputData = readFile("datasets/A-100-30.json");
        SetupList setups = inputData.generateSetupList();
        List<Job> jobs = inputData.getJobsSortedReleaseDate();
        List<UnavailablePeriod> unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();

        calculateInitialSolution(setups, jobs, unavailablePeriods);

        System.out.println("Unavailable periods: \n"+unavailablePeriods);
        System.out.println("scheduled: \n"+scheduledTasks);
        System.out.println("waiting: \n"+waitingJobs);

        // Write to JSON-file
        OutputData outputData = generateOutput("outputFile_Wout_Wannes_Matthieu", 100, scheduledTasks);
        writeFile("solutions/sol-A-100-30.json", outputData);
    }

    public static void calculateInitialSolution(SetupList setups, List<Job> jobs, List<UnavailablePeriod> unavailablePeriods) {
        boolean maxReached = false;
        for(Job job : jobs) {
            if(!scheduledTasks.isEmpty()) {
                // Consistently check if horizon is reached
                if(scheduledTasks.getLast().getFinishDate() == horizon || maxReached) {
                    queueJob(job);
                }
                else if(scheduledTasks.getLast().getFinishDate() > horizon ) {
                    maxReached =true;
                    Job j = (Job)scheduledTasks.getLast();
                    // Remove last job + the linked setup
                    scheduledTasks.removeLast();
                    scheduledTasks.removeLast();
                    queueJob(j);
                }
                else {
                    scheduleJob_FirstFit(job, setups, unavailablePeriods);
                }
            }
            else {
                scheduleJob_FirstFit(job, setups, unavailablePeriods);
            }
        }
        if(scheduledTasks.getLast().getFinishDate() > horizon) {
            Job j = (Job) scheduledTasks.getLast();
            // Remove last job + the linked setup
            scheduledTasks.removeLast();
            scheduledTasks.removeLast();
            queueJob(j);
        }


        // todo : fill up holes
    }

    private static void scheduleJob_FirstFit(Job job, SetupList setups, List<UnavailablePeriod> unavailablePeriods) {
        // If scheduled is not empty - there needs to be a setup
        if(!scheduledTasks.isEmpty()) {
            Task previous = scheduledTasks.getLast();

            // Schedule setup
            int startingDateSetup = previous.finishDate+1;
            Setup setup = setups.getSetup(((Job) previous).getId(), job.getId());
            int durationSetup = setup.getDuration();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the setup
                if(startingDateSetup < up.getStartDate() && startingDateSetup + durationSetup < up.getStartDate()) break;
                else {
                    startingDateSetup = Math.max(up.getFinishDate() + 1, startingDateSetup);
                }
            }

            // Schedule job
            // Point from which job can be scheduled
            int startingDateJob = Math.max(job.getReleaseDate(), startingDateSetup + durationSetup +1);
            int durationJob = job.getDuration();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the job
                if(startingDateJob < up.getStartDate() && startingDateJob + durationJob < up.getStartDate()) break;
                else {
                    startingDateJob = Math.max(up.getFinishDate() + 1, startingDateJob);
                }
            }
            planJobSetup(setup, startingDateSetup, job, startingDateJob);
        }
        // If the list is empty, no need to take setup in account
        else {
            // List is empty
            // Make sure job is scheduled in first possible slot (no unavailable periods)
            int duration = job.getDuration();
            int startingDate = job.getReleaseDate();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the job
                if(startingDate < up.getStartDate() && startingDate + duration < up.getStartDate()) break;
                else {
                    startingDate = up.getFinishDate() + 1;
                }
            }
            planJob(job, startingDate);
        }
    }

    private static void planJobSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
        if(job.makesDueDate(startingDateJob)) {
            setup.setStartDate(startingDateSetup);
            setup.calculateFinishDate();
            scheduledTasks.addLast(setup);

            job.setStartDate(startingDateJob);
            job.calculateFinishDate();
            scheduledTasks.addLast(job);
        }
        else {
            queueJob(job);
        }
    }

    // Plan job to waitingJobs or ScheduledTasks based on whether it makes the deadline
    public static void planJob(Job job, int startingDate) {
        if(job.makesDueDate(startingDate)) {
            job.setStartDate(startingDate);
            job.calculateFinishDate();
            scheduledTasks.addLast(job);
        }
        else {
            queueJob(job);
        }
    }
    private static void queueJob(Job job) {
        job.setStartDate(-1);
        job.setFinishDate(-1);
        waitingJobs.addLast(job);
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

    public static void writeFile(String path, OutputData outputData) {
        try {
            Gson gson = new Gson();
            gson.toJson(outputData, new FileWriter(path));
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

//    private static void writeFile(String path) {
//        String json = "";
//        json = json + "{ " + "\"name\": " + "\"TEMPSOLUTION_A-100-30.json\"\n" + "\"value\": " + 0.00 + ",\n" + "\"jobs\": [\n";
//
//
//        for(Task t : scheduledTasks) {
//            if(t.getClass() == Job.class) json = json + "{" + (Job)t + "},";
//        }
//        json += "],";
//        json += "setups: [";
//        for(Task t : scheduledTasks) {
//            if(t.getClass() == Setup.class) json = json + "{" + (Setup)t + "},";
//        }
//        json += "],";
//        System.out.println(new Gson().toJson(json));
//    }



}


