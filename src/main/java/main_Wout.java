import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

        // todo: write to json
        // Write to JSON-file
        // writeFile("calculatedSolution/TEMPSOLUTION_A-100-30.json");
    }

    public static void calculateInitialSolution(SetupList setups, List<Job> jobs, List<UnavailablePeriod> unavailablePeriods) {
        // Check if the horizon is reached
        boolean maxReached = false;
        for(Job job : jobs) {
            if(!scheduledTasks.isEmpty()) {
                // Consistently check if horizon is reached
                // If the last element finishes exactly on the horizon - queue everything
                if(scheduledTasks.getLast().getFinishDate() == horizon || maxReached) {
                    queueJob(job);
                }
                // If the horizon is exceeded - remove last job + setup, and queue everything
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
        // Check if the last added job doesn't exceed the horizon
        if(scheduledTasks.getLast().getFinishDate() > horizon) {
            Job j = (Job) scheduledTasks.getLast();
            // Remove last job + the linked setup
            scheduledTasks.removeLast();
            scheduledTasks.removeLast();
            queueJob(j);
        }

        // todo : fill up holes in between jobs
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
        // If the list is empty, no need to take setup in account -> no prior job
        else {
            // List is empty
            // Make sure job is scheduled in first possible slot (no unavailable periods)
            int duration = job.getDuration();
            int startingDate = job.getReleaseDate();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the job
                if(startingDate < up.getStartDate() && startingDate + duration < up.getStartDate()) break;
                else {
                    // The current time from which the job can be scheduled: finishtime of the unavailable period
                    startingDate = up.getFinishDate() + 1;
                }
            }
            planJob(job, startingDate);
        }
    }

    // Finally schedule job together with the corresponding setup
    private static void planJobSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
        // Check if it is possible to schedule the job/setup
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
            System.out.println(jsonString);
            Gson gson = new Gson();
            inputData = gson.fromJson(jsonString, InputData.class);
        }
        catch (IOException e) {e.printStackTrace();}
        return inputData;
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


