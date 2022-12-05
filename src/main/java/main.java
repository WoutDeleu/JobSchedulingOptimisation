import com.github.sh0nk.matplotlib4j.Plot;
import com.github.sh0nk.matplotlib4j.PythonExecutionException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class main {
    // Input data
    private static List<Job> allJobs;
    private static SetupList setups;
    private static List<UnavailablePeriod> unavailablePeriods;
    private static int horizon;
    private static double weight;

    // Temporary results
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static LinkedList<Job> jobsToShuffle;
    private static LinkedList<Job> toRemove = new LinkedList<>();
    private static LinkedList<Task> scheduledTasks;
    private static double currentValue;

    // Optimal result
    private static LinkedList<Task> bestSchedule = new LinkedList<>();
    private static double bestValue;


    private static int NR_OF_INITIAL_PLANNED;
    private static final int NR_ITERATIONS_BEFORE_ACCEPT = 15;//TODO: nog wat zoeken naar ideale waarden,

    private static final double MARGIN = 1.001;
    private static boolean FULL_SOLUTION = false;
    private static boolean GRAPHICS = true;
//    private static final long availableTime = 1000*60*10; // 10 min
    private static final long availableTime = 1000*60; // 1 min
    private static String current_name = "";

    private static Random random = new Random();

    public static void main(String[] args) {
        if(FULL_SOLUTION) {allsolutions();}
        // Calculate solutions for manually given datasets
        else {
            // Reading inputs
            InputData inputData = InputData.readFile("datasets/A-100-30.json");
            current_name = inputData.getName();
            System.out.print(inputData.getName());
            setups = inputData.generateSetupList();
            allJobs = inputData.getJobsSortedReleaseDate();
            unavailablePeriods = inputData.getUnavailablePeriods();
            horizon = inputData.getHorizon();
            weight = inputData.getWeightDuration();

            // Initial solution
            scheduledTasks = new LinkedList<>();
            jobsToShuffle = new LinkedList<>(allJobs);
            makeFeasibleSolution();
            currentValue = calculateCost(false);
            bestValue = currentValue;
            bestSchedule = deepClone(scheduledTasks);

            // Get some reference values
            NR_OF_INITIAL_PLANNED = jobsToShuffle.size();

            // Local search
            localSearch();

            printEarlinessPenaltiesPerJob();

            // Write to JSON-file
            OutputData outputData = InputData.generateOutput(inputData.getName(), bestValue, bestSchedule);
            InputData.writeFile("calculatedSolution/sol_" + inputData.getName() + ".json", outputData);

        }
    }


    /*********************************** LOCAL SEARCH ***********************************/
    public static void localSearch() {
        double T = 1;
        long timeStart = System.currentTimeMillis();
        long timeNow = timeStart;

        // For time plot
        ArrayList<Long> times = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();

        LinkedList<Task> oldScheduling;
        LinkedList<Job> oldWaitingJobs;
        LinkedList<Job> oldJobsToShuffle;

        while (timeNow<timeStart+availableTime) {
            // Save the current situation
            oldScheduling = deepClone(scheduledTasks);
            oldWaitingJobs = deepCloneJobs(waitingJobs);
            oldJobsToShuffle = deepCloneJobs(jobsToShuffle);

            executeRandomIntelligentOperation();
            executeRandomBasicOperation();

            makeFeasibleSolution();

            if(GRAPHICS) {
                long currTime = System.currentTimeMillis() - timeStart;
                times.add(currTime);
                values.add((long) currentValue);
            }

            double tempCost = calculateCost(false);
            double deltaE = tempCost - currentValue;
            double p = Math.exp(-deltaE/T);
            double r = random.nextDouble();

            // If all-time best, safe this solution
            if (currentValue<bestValue) {
                bestValue = currentValue;
                bestSchedule = deepClone(scheduledTasks);
            }

            // Accept better solution
            if (tempCost < currentValue) {
                long time = (timeNow-timeStart)/1000;
                System.out.println("Verbetering gevonden! Cost: "+tempCost+", na "+time+" seconden");

                currentValue = tempCost;
            }
            // Accept worse solution in some cases
            else if (p>r) {
                currentValue = tempCost;
                long time = (timeNow-timeStart)/1000;
                System.out.println("Slechter geacepteerd! Cost: "+tempCost+", na "+time+" seconden");
            }
            // Reset old solution
            else {
                scheduledTasks = oldScheduling;
                waitingJobs = oldWaitingJobs;
                jobsToShuffle = oldJobsToShuffle;
            }

            timeNow = System.currentTimeMillis();
            T = 0.99*T;
        }
        System.out.println(" cost=" + currentValue);

        if(GRAPHICS) {
            try {Plotter.plotTimes(times, values, current_name);}
            catch (PythonExecutionException | IOException e) {System.out.println(e);}
        }
    }
    public static void executeRandomIntelligentOperation() {
        int option = random.nextInt(5);
        switch (option) {
            case 0 -> smartDelete();
            case 1 -> localSwap();
            case 2 -> smartInsert();
            case 3 -> moveJob();
            case 4 -> removeJobHighestRejectionPenalty();
        }
    }
    public static void executeRandomBasicOperation() {
        int jobIndex = random.nextInt(jobsToShuffle.size());
        int option = random.nextInt(4);
        if(calculateRejectionCost()>2*calculateDurationCost()) option = 2;
        switch (option) {
            case 0: if (jobsToShuffle.size()>10) //todo betere logica
                operation_deleteJob(jobIndex);
                break;
            case 1: operation_swapJobs(jobIndex, random.nextInt(jobsToShuffle.size())); break;
            case 2: // insert
                if (!waitingJobs.isEmpty()) {
                    int waitingIndex = random.nextInt(waitingJobs.size());
                    operation_insertJob(jobIndex, waitingJobs.get(waitingIndex));
                }
                break;
            case 3: // Iets beter als we het niet gebruiken
//                int jobIndex2 = random.nextInt(jobsToShuffle.size());
//                TwoOptSwap(jobIndex, jobIndex2);
        }
    }
    /*********************************** LOCAL SEARCH ***********************************/


    /*********************************** BASIC OPERATIONS ***********************************/
    public static void operation_deleteJob(int index) {
        assert index>=0 : "No negative index allowed";
        assert !jobsToShuffle.isEmpty() : "No jobs left to remove";

        Job job = jobsToShuffle.get(index);
        jobsToShuffle.remove(job);
        queueJob(job);
    }
    public static void operation_insertJob(int index, Job job) {
        assert index>=0 : "No negative index allowed";
        assert index<=jobsToShuffle.size() : "Index valt buiten de array";

        jobsToShuffle.add(index, job);
        toRemove.remove(job);
        waitingJobs.remove(job);
    }
    public static void operation_swapJobs(int i1, int i2) {
        // Correct for 2 matching indexes
        if (i1 == i2) {
            if (i1==0) {
                if (jobsToShuffle.size()>1) i2 += 1;
                else return; // No swap possible of only one job in scheduledTasks
            }
            else i1 -= 1;
        }

        int index1 = Math.min(i1, i2);
        int index2 = Math.max(i1, i2);

        Job job1 = jobsToShuffle.get(index1);
        Job job2 = jobsToShuffle.get(index2);

        operation_deleteJob(index2);
        operation_deleteJob(index1);

        operation_insertJob(index1, job2);
        operation_insertJob(index2, job1);
    }
    /*********************************** BASIC OPERATIONS ***********************************/


    /*********************************** ADVANCED OPERATIONS ***********************************/
    public static void smartDelete() {
        int jobIndex = random.nextInt(jobsToShuffle.size());
        if (jobsToShuffle.size() > 0.2*NR_OF_INITIAL_PLANNED) {
            operation_deleteJob(jobIndex);
        }
    }
    public static void localSwap() {
        int index1 = random.nextInt(jobsToShuffle.size());
        int index2;
        if (index1==0) index2=1;
        else index2 = index1-1;
        operation_swapJobs(index1,index2);
    }
    public static void smartInsert() {
        int jobIndex = random.nextInt(jobsToShuffle.size());
        if (!waitingJobs.isEmpty()) {
            int waitingIndex = random.nextInt(waitingJobs.size());
            operation_insertJob(jobIndex, waitingJobs.get(waitingIndex));
        }
    }
    public static void moveJob() {
        int i  = getRandomScheduledJobIndex();
        if(i != -1) {
            Job j = jobsToShuffle.get(i);
            int t = j.getStartDate();
            int index = i+1;
            while (t < j.getDueDate()) {
                t += jobsToShuffle.get(index).getDuration();
                if(index<jobsToShuffle.size()-1) index++;
                else t = j.getDueDate();
            }
            operation_deleteJob(i);
            operation_insertJob(index, j);
        }
    }
    public static void removeJobHighestRejectionPenalty(){
        operation_deleteJob(highestEarlinessPenalty());
    }
    public static void TwoOptSwap(int index1, int index2) {
        LinkedList<Job> temp = new LinkedList<>();
    /*if (index1 == index2) {
        if (index1==0) {
            if (jobsToShuffle.size()>1) index2 += 1;
            else return; // No swap possible of only one job in scheduledTasks
        }
        else index1 -= 1;
    }*/
        int i1 = Math.min(index1, index2);
        int i2 = Math.max(index1, index2);

        while (i2-i1>10) {
            i2-=1;
            i1+=1;
        }

//        System.out.println("i1: "+i1+", i2: "+i2);
        int i;
//        for(i = 0; i < jobsToShuffle.size()-1; i++) {
//            System.out.print(i + ": " + jobsToShuffle.get(i) + " ->\t" );
//        }
//        System.out.println(i + ": " + jobsToShuffle.get(i));


        //alle jobs van index 0 tot voor i1 in zelfde volgorde toevoegen
        for (int a = 0; a < i1; a++) {
            temp.add(jobsToShuffle.get(a));
        }
        //alle jobs tussen (inclusief) i1 en i2 in omgekeerde volgorde toevoegen aan nieuwe scheduling
        for (int b = i2; b >= i1 ; b--) {
            temp.add(jobsToShuffle.get(b));
        }
        //overige jobs van i2 tot jobsToShuffle opnieuw in zelfde volgorde toevoegen
        for (int c = i2+1; c < jobsToShuffle.size(); c++) {
            temp.add(jobsToShuffle.get(c));
        }
        jobsToShuffle = new LinkedList<>(temp);
    /*int j;
    for(j = 0; j < jobsToShuffle.size()-1; j++) {
        System.out.print(j + ": " + jobsToShuffle.get(j) + " ->\t" );
    }
    System.out.println(j + ": " + jobsToShuffle.get(j));*/
    }
    /*********************************** ADVANCED OPERATIONS ***********************************/


    /*********************************** MAKE FEASIBLE ***********************************/
    public static void makeFeasibleSolution() {
        // TODO herbereken maar vanaf bepaalde index
        // Clear old schedule
        scheduledTasks.clear();

        boolean maxReached = false;

        for(Job job : jobsToShuffle) {
            if(!maxReached) {
                scheduleJob(job);
                if (job.getFinishDate()>=horizon) {
                    trim();
                    maxReached=true;
                }
            }
            else{queueJob(job);}
        }
        for (Job job : toRemove) jobsToShuffle.remove(job);
    }
    private static void scheduleJob(Job job) {
        // No setup for first job
        if (scheduledTasks.isEmpty()) {
            int startDate = job.getReleaseDate();
            startDate = calculateFittingStartTimeUPs(startDate, job);

            planAndPlaceJob(job, startDate);
        }
        // All others require a setup
        else {
            Task previous = scheduledTasks.getLast();
            assert previous instanceof Job : "Laatste item is geen job";

            // Schedule setup
            int startDateSetup = previous.finishDate+1;
            Setup setup = setups.getSetup((Job) previous, job);
            startDateSetup = calculateFittingStartTimeUPs(startDateSetup, setup);

            // Schedule job
            int startDateJob = Math.max(job.getReleaseDate(), startDateSetup + setup.getDuration());
            startDateJob = calculateFittingStartTimeUPs(startDateJob, job);

            planAndPlaceJobWithSetup(setup, startDateSetup, job, startDateJob);
        }
    }
    public static void trim() {
        Job lastJob = (Job) scheduledTasks.getLast();
        while (lastJob.getStartDate()>horizon) {
            scheduledTasks.removeLast();
            scheduledTasks.removeLast();
            queueJob(lastJob);
        }
    }
    private static void queueJob(Job job) {
        waitingJobs.add(job);
        toRemove.add(job);
        job.setJobSkipped();
    }
    private static int calculateFittingStartTimeUPs(int startDate, Task task) {
        int finishDate = startDate + task.getDuration()-1;
        for (UnavailablePeriod up : unavailablePeriods) {
            if (finishDate < up.getStartDate()) break;
            else startDate = Math.max(up.getFinishDate() + 1, startDate);
        }
        return startDate;
    }
    public static void planAndPlaceJob(Job job, int startDate) {
        if(job.makesDueDate(startDate)) {
            job.setStartDate(startDate);
            job.calculateFinishDate();
            scheduledTasks.add(job);
        }
        else {queueJob(job);}
    }
    private static void planAndPlaceJobWithSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
        if (job.makesDueDate(startingDateJob)) {
            setup.setStartDate(startingDateSetup);
            setup.calculateFinishDate();
            scheduledTasks.add(setup);

            job.setStartDate(startingDateJob);
            job.calculateFinishDate();
            scheduledTasks.add(job);
        }
        else {
            queueJob(job);
        }
    }
    /*********************************** MAKE FEASIBLE ***********************************/


    /*********************************** ASSISTING FUNCTIONS ***********************************/
//    public static double calculateCost() {
//        double cost = 0;
//        for (Job job : allJobs) cost += job.getCost();
//        if (!scheduledTasks.isEmpty()) // add total schedule duration
//            cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
//        return (double) Math.round(cost * 100) / 100;
//    }
    public static double calculateCost(boolean print) {
        double earliness = 0;
        for (Task task : scheduledTasks)
            if (task instanceof Job job)
                earliness += job.getScheduledCost();
        double rejection = 0;
        for (Job job : waitingJobs)
            rejection += job.getWaitingCost();
        double duration = 0;
        if (!scheduledTasks.isEmpty()) // add total schedule duration
            duration = (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        if(print) {
            System.out.println("earliness: "+earliness);
            System.out.println("rejection: "+rejection);
            System.out.println("duration: "+duration);
        }
        return (double) Math.round((earliness+rejection+duration) * 100) / 100;
    }
    public static double calculateRejectionCost() {
        double cost = 0;
        for(Job j : allJobs) {
            if(j.getStartDate()<0) {
                cost+=j.getCost();
                /*if(!waitingJobs.contains(j)) {
                    System.out.println("Job "+j.getId()+"niet in waitinglist maar ook niet gescheduled, startTime=-1");
                }*/
            }
        }
        return cost;
    }
    public static double calculateEarlinessCost() {
        double cost = 0;
        for(Job j : allJobs) {
            if(j.getStartDate()>=0) {
                cost+=j.getCost();
                /*if(!scheduledTasks.contains(j)) {
                    System.out.println("Job "+j.getId()+"niet gescheduled maar ook niet in waiting list, startTime>0");
                }*/
            }
        }
        return cost;
    }
    public static double calculateDurationCost() {
        double cost = 0;
        if (!scheduledTasks.isEmpty()) // add total schedule duration
            cost = (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
    }
    public static double calculateCostSeparate(){
        double rej = calculateRejectionCost();
        double earliness = calculateEarlinessCost();
        double dur = calculateDurationCost();
        System.out.println(rej+earliness+dur+" (rejection: "+rej+" + earliness: "+earliness+" + duration: "+dur+")");
        return rej+earliness+dur;
    }
    public static LinkedList<Task> deepClone(LinkedList<Task> tasks) {
        LinkedList<Task> clone = new LinkedList<>();
        for(Task t : tasks) clone.add(t.clone());
        return clone;
    }
    public static LinkedList<Job> deepCloneJobs(LinkedList<Job> jobs) {
        LinkedList<Job> clone = new LinkedList<>();
        for(Job job : jobs) clone.add(job.clone());
        return clone;
    }
    public static int getRandomScheduledJobIndex() {
        Random r = new Random();
        int jobIndex = r.nextInt(jobsToShuffle.size()-1);
        int i = jobIndex;
        while (i<jobsToShuffle.size()-1) {
            if(scheduledTasks.contains(jobsToShuffle.get(i))) return i;
            i++;
        }
        i = 0;
        while (i < jobIndex) {
            if(scheduledTasks.contains(jobsToShuffle.get(i))) return i;
            i++;
        }
        return -1;
    }
    public static int highestEarlinessPenalty() {
        double cost = 0;
        int i = -1;
        for(Job j : jobsToShuffle) {
            if(j.getStartDate()>=0 && j.getCost()>cost) {
                cost=j.getCost();
                i = jobsToShuffle.indexOf(j);
            }
        }
        return i;
    }
    /*********************************** ASSISTING FUNCTIONS ***********************************/


    /*********************************** PRINTS ***********************************/
    public static void printScheduledTasks(String comment) {
        System.out.print(comment+": \t");
        printScheduledTasks();
    }
    public static void printScheduledTasks() {
        if(scheduledTasks.isEmpty()) {
            System.out.println("scheduledTasks is empty");
            return;
        }
        int i;
        for(i = 0; i < scheduledTasks.size()-1; i++) {
            System.out.print(i + ": " + scheduledTasks.get(i) + " ->\t" );
        }
        System.out.println(i + ": " + scheduledTasks.get(i));
    }
    /*********************************** PRINTS ***********************************/



    /*********************************** Full solution ***********************************/
    public static void allsolutions() {
        // Reading inputs
        File[] datasets = InputData.getFiles("datasets");
        for(File dataset : datasets) {
            InputData inputData = InputData.readFile("datasets/" + dataset.getName());
            System.out.print(inputData.getName());
            current_name = inputData.getName();
            setups = inputData.generateSetupList();
            allJobs = inputData.getJobsSortedReleaseDate();
            unavailablePeriods = inputData.getUnavailablePeriods();
            horizon = inputData.getHorizon();
            weight = inputData.getWeightDuration();

            // Initial solution
            scheduledTasks = new LinkedList<>();
            jobsToShuffle = new LinkedList<>(allJobs);
            makeFeasibleSolution();
            currentValue = calculateCost(false);

            // Get some reference values
            NR_OF_INITIAL_PLANNED = jobsToShuffle.size();

            // Local search
            localSearch();

            /*calculateCostSeparate();
            System.out.println(calculateCost());
            System.out.println(currentBestValue);*/

            System.out.println(" cost="+currentValue);

            // Write to JSON-file
            OutputData outputData = InputData.generateOutput(inputData.getName(), currentValue, scheduledTasks);
            InputData.writeFile("calculatedSolution/sol_" + inputData.getName()+".json", outputData);
            reset();
        }
    }
    private static void reset() {
        allJobs.clear();
        setups.clear();
        unavailablePeriods.clear();
        horizon = 0;
        waitingJobs.clear();
        jobsToShuffle.clear();
        toRemove.clear();
        bestSchedule.clear();
    }
    /*********************************** Full solution ***********************************/

}

