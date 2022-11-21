import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class main {

    private static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static List<Job> jobs = new LinkedList<>();
    private static SetupList setups;
    private static int horizon;
    private static int currentBestValue;
    private static double weight;

    public static void main(String[] args) {

        InputData inputData = readFile("datasets/TOY-20-10.json");
        setups = inputData.generateSetupList();
        jobs = inputData.getJobsSortedReleaseDate();
        List<UnavailablePeriod> unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();
        weight = inputData.getWeightDuration();

        calculateInitialSolution(setups, jobs, unavailablePeriods);

//        System.out.println("Unavailable periods: \n"+unavailablePeriods);
//        System.out.println("scheduled: \n"+scheduledTasks);
//        System.out.println("waiting: \n"+waitingJobs);

        double cost = calculateCost();
        System.out.println(cost);

        // Write to JSON-file
        OutputData outputData = generateOutput("TOY-20-10", cost, scheduledTasks);
        writeFile("calculatedSolution/sol-TOY-20-10.json", outputData);
    }

    public static void calculateInitialSolution(SetupList setups, List<Job> jobs, List<UnavailablePeriod> unavailablePeriods) {
        boolean maxReached = false;
        for(Job job : jobs) {
            if(!scheduledTasks.isEmpty()) { // In case of first job, we can't reach previous job
                // Consistently check if horizon is reached
                Job previous = (Job) scheduledTasks.getLast();
                if (previous.getFinishDate() == horizon || maxReached) {
                    queueJob(job);
                }
                else if (previous.getFinishDate() > horizon) {
                    maxReached = true;
                    // Remove last job + the linked setup
                    scheduledTasks.removeLast();
                    scheduledTasks.removeLast();
                    queueJob(previous);
                }
                else {
                    scheduleJob_EndOfLine(job, setups, unavailablePeriods);
                }
            }
            else {
                scheduleJob_EndOfLine(job, setups, unavailablePeriods);
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

    private static void scheduleJob_EndOfLine(Job job, SetupList setups, List<UnavailablePeriod> unavailablePeriods) {
        // If scheduled is not empty - there needs to be a setup
        if(!scheduledTasks.isEmpty()) {
            Task previous = scheduledTasks.getLast();

            // Schedule setup
            int startingDateSetup = previous.finishDate+1;
            Setup setup = setups.getSetup(((Job) previous).getId(), job.getId());
            int durationSetup = setup.getDuration();
            for (UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the setup
                // Does setup lie before up?
                if(startingDateSetup + durationSetup-1 < up.getStartDate()) {
                    break; // can be scheduled starting from this startDate
                }
                else {
                    startingDateSetup = Math.max(up.getFinishDate() + 1, startingDateSetup);
                }
            }

            // Schedule job
            // Point from which job can be scheduled
            int startingDateJob = Math.max(job.getReleaseDate(), startingDateSetup + durationSetup);
            int durationJob = job.getDuration();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the job
                if (startingDateJob < up.getStartDate() && startingDateJob + durationJob < up.getStartDate()) break;
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
                if(startingDate < up.getStartDate() && startingDate + duration-1 < up.getStartDate()) break;
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
        job.setJobSkipped();
        waitingJobs.addLast(job);
    }

    public static double calculateCost() {
        double cost = 0;
        for (Job job : jobs) {
            cost += job.getCost();
        }
        cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
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
            //Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
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

    // Runs over schedule
    // Removes scheduled task which clash with the unavailability periods
    public static void makeFeasible(int index, List<UnavailablePeriod> unavailablePeriods) {
        for(int i=index; i<scheduledTasks.size(); i++) {
            Task t = scheduledTasks.get(i);
            // If task is scheduled after ALL the unavailable periods
            if(t.getStartDate() > unavailablePeriods.get(unavailablePeriods.size()-1).getFinishDate()){
                break;
            }
            /* if(t.getClass()==Job.class){
                Job job = (Job) t;
                if(job.getStartDate()<job.getReleaseDate()){
                    scheduledTasks.remove(job);
                }
            }*/

            for(UnavailablePeriod u : unavailablePeriods) {
                // If task of task is executed during unavailability period
                // It needs to be taken out (T1 -> S12 -> T2 -> S23 -> T3 ->) => (T1 -> S13 -> T3)
                // Remark: links must be restored (no dangling links)
                if((t.getStartDate() >= u.getStartDate() && t.getStartDate() <= u.getFinishDate()) || (u.getStartDate()<=t.getFinishDate() && t.getFinishDate()<=u.getFinishDate())) {
                    if(t.getClass()==Job.class){
                        // remove job and setup necessary for that job
                        scheduledTasks.remove(i);
                        scheduledTasks.remove(i-1);

                        // reconnect links
                        Job j = (Job) scheduledTasks.get(i-2);
                        Setup s = (Setup) scheduledTasks.get(i+1);
                        scheduledTasks.remove(s);
                        Setup s2 = setups.getSetup(j.getId(), s.getJob2());
                        scheduledTasks.add(i+1, s2);
                        //break;
                    }
                    if(t.getClass()==Setup.class) {
                        //remove setup in unavailable period and job after setup in unavailable period
                        // + replace setup remaining after job after setup in unavailable period
                        scheduledTasks.remove(i);
                        scheduledTasks.remove(i+1);

                        // reconnect links
                        Job j = (Job) scheduledTasks.get(i-1);
                        // Setup s = (Setup) scheduledTasks.get(i-2);
                        Setup s = (Setup) scheduledTasks.get(i+2);
                        scheduledTasks.remove(s);
                        Setup s2 = setups.getSetup(j.getId(), s.getJob2());
                        scheduledTasks.add(i+2, s2);
                    }
                }
            }
        }
    }
}



