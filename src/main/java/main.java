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
    private static double currentBestValue;

    // Parameters
    // Number of basic operations before we generate a feasible solution and calculate the associated cost
    private static final int NR_OF_ITERATIONS_BEFORE_CALCULATE = 1; //



    public static void main(String[] args) {
        // Reading inputs
        InputData inputData = InputData.readFile("datasets/A-100-30.json");
        setups = inputData.generateSetupList();
        allJobs = inputData.getJobsSortedReleaseDate();
        unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();
        weight = inputData.getWeightDuration();

        // Initial solution
        scheduledTasks = new LinkedList<>();
        jobsToShuffle = new LinkedList<>(allJobs);
        makeFeasibleSolution();
        currentBestValue = calculateCost();

        // Local search
        localSearch();

        // Write to JSON-file
        OutputData outputData = InputData.generateOutput(inputData.getName(), currentBestValue, scheduledTasks);
        InputData.writeFile("calculatedSolution/sol_" + inputData.getName()+".json", outputData);
    }


    /*********************************** LOCAL SEARCH ***********************************/
    public static void localSearch() {
        int iterationCount = 0;
        LinkedList<Task> oldScheduling;
        LinkedList<Job> oldWaitingJobs;

        while (iterationCount<10000) {
            // Save the current situation
            oldScheduling = deepClone(scheduledTasks);
            oldWaitingJobs = deepCloneJobs(waitingJobs);

            // Do some operations and make the result feasible
            executeRandomBasicOperation();
            makeFeasibleSolution();

            double tempCost = calculateCost();

            // Replace the current solution if the new solution scores better
            // Else reset the old solution
            if (iterationCount%NR_OF_ITERATIONS_BEFORE_CALCULATE == 0) {
                if (currentBestValue > tempCost) {
                    currentBestValue = tempCost;
                } else {
                    scheduledTasks = oldScheduling;
                    waitingJobs = oldWaitingJobs;
                }
            }

            printScheduledTasks("local search, cost="+currentBestValue);
            iterationCount++;
        }
    }
    public static void executeRandomBasicOperation() {
        Random rand = new Random();
        int jobIndex = rand.nextInt(jobsToShuffle.size());
        switch (rand.nextInt(3)) {
            case 0: if (jobsToShuffle.size()>10) //todo betere logica
                operation_deleteJob(jobIndex);
                break;
            case 1: operation_swapJobs(jobIndex, rand.nextInt(jobsToShuffle.size())); break;
            case 2: // insert
                if (!waitingJobs.isEmpty()) {
                    int waitingIndex = rand.nextInt(waitingJobs.size());
                    operation_insertJob(jobIndex, waitingJobs.get(waitingIndex));
                }
                break;
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
    public static double calculateCost() {
        double cost = 0;
        for (Job job : allJobs) cost += job.getCost();
        if (!scheduledTasks.isEmpty()) // add total schedule duration
            cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
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
}

