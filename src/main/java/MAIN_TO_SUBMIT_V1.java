import java.util.*;

public class MAIN_TO_SUBMIT_V1 {
    // Input data
    private static List<Job> allJobs;
    private static SetupList setups;
    private static List<UnavailablePeriod> unavailablePeriods;
    private static List<UnavailablePeriod> inverseUnavailablePeriods;
    private static int horizon;
    private static double weight;
    private static int NR_OF_INITIAL_PLANNED;
    // Temporary results
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static LinkedList<Job> jobsToShuffle;
    private static LinkedList<Job> toRemove = new LinkedList<>();
    private static LinkedList<Task> scheduledTasks;
    private static double currentValue;

    // Optimal result
    private static LinkedList<Task> bestSchedule = new LinkedList<>();
    private static double bestValue;
    private static long availableTime;
    private static Random random;


    public static void main(String[] arguments) {
        String inputFile_name = arguments[0];
        String solutionFile_name = arguments[1];
        random = new Random(Integer.parseInt(arguments[2]));
        // 500 ms less than given, to be sure
        availableTime = Long.parseLong(arguments[3]) * 1000;
        int NR_OF_THREADS = Integer.parseInt(arguments[4]);

        InputData inputData = InputData.readFile(inputFile_name);
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

        // Write to JSON-file
        OutputData outputData = InputData.generateOutput(inputData.getName(), currentValue, scheduledTasks);
        InputData.writeFile(solutionFile_name, outputData);
    }

    /*********************************** LOCAL SEARCH ***********************************/
    public static void localSearch() {
        int iterationCount = 0;
        double inverseBias = 0.5; // The bigger this number gets the more we execute inverseMakeFeasibleSolution()
        boolean inverse = false;
        double T = 1;
        long timeStart = System.currentTimeMillis();
        long timeNow = timeStart;
//        int resetCounter = 0;
//        boolean hasReset = false;

        LinkedList<Task> oldScheduling;
        LinkedList<Job> oldWaitingJobs;
        LinkedList<Job> oldJobsToShuffle;
        ArrayList<Double> differences = new ArrayList<>();

        while (timeNow < timeStart + availableTime) {
            // Save the current situation
            oldScheduling = deepClone(scheduledTasks);
            oldWaitingJobs = deepCloneJobs(waitingJobs);
            oldJobsToShuffle = deepCloneJobs(jobsToShuffle);

            executeRandomBasicOperation();
            executeRandomIntelligentOperation();

            double x = random.nextDouble();
            if (x < inverseBias) {
                inverseMakeFeasibleSolution();
                inverse = true;
            } else {
                makeFeasibleSolution();
                inverse = false;
            }

            double tempCost = calculateCost(false);

            // Accept better solution
            if (tempCost < currentValue) {
//                if(currentValue - tempCost < currentValue*0.001) {
//                    resetCounter++;
//                }
//                else {
//                    resetCounter = 0;
//                }
                if (inverse && inverseBias < 0.95) inverseBias += 0.01;
                else if (!inverse && inverseBias > 0.05) inverseBias -= 0.01;

                long time = (timeNow - timeStart) / 1000;

                currentValue = tempCost;

                // If all-time best, safe this solution
                if (currentValue < bestValue) {
                    bestValue = currentValue;
                    bestSchedule = deepClone(scheduledTasks);
                }
            } else {
                double deltaE = tempCost - currentValue;
                double p = Math.exp(-deltaE / T);
                double r = random.nextDouble();
                // Accept worse solution in some cases
//                if (p >= r || hasReset) {
                if (p >= r) {
                    currentValue = tempCost;
                    long time = (timeNow - timeStart) / 1000;
//                    hasReset = false;

                }

                // Reset old solution
                else {
                    scheduledTasks = oldScheduling;
                    waitingJobs = oldWaitingJobs;
                    jobsToShuffle = oldJobsToShuffle;
//                    resetCounter++;
                }
            }
//            if(resetCounter > 1000 && !hasReset) {
//                for(int i=0; i<allJobs.size()*0.001; i++) {
//                    ruinAndRecreate(random.nextInt(jobsToShuffle.size()));
//                }
//                resetCounter = 0;
//                hasReset = true;
//            }
            timeNow = System.currentTimeMillis();
            T = 0.995 * T;
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
        if (calculateRejectionCost() > 2 * calculateDurationCost()) option = 2;
        switch (option) {
            case 0:
                if (jobsToShuffle.size() > 10) {
                    operation_deleteJob(jobIndex);
                    break;
                }
            case 1:
                operation_swapJobs(jobIndex, random.nextInt(jobsToShuffle.size()));
                break;
            case 2: // insert
                if (!waitingJobs.isEmpty()) {
                    int waitingIndex = random.nextInt(waitingJobs.size());
                    operation_insertJob(jobIndex, waitingJobs.get(waitingIndex));
                }
                break;
            case 3:
                ruinAndRecreate(jobIndex);
        }
    }

    /*********************************** LOCAL SEARCH ***********************************/


    /*********************************** BASIC OPERATIONS ***********************************/
    public static void operation_deleteJob(int index) {
        assert index >= 0 : "No negative index allowed";
        assert !jobsToShuffle.isEmpty() : "No jobs left to remove";

        Job job = jobsToShuffle.get(index);
        jobsToShuffle.remove(job);
        queueJob(job);
    }

    public static void operation_insertJob(int index, Job job) {
        assert index >= 0 : "No negative index allowed";
        assert index <= jobsToShuffle.size() : "Index valt buiten de array";

        jobsToShuffle.add(index, job);
        toRemove.remove(job);
        waitingJobs.remove(job);
    }

    public static void operation_swapJobs(int i1, int i2) {
        // Correct for 2 matching indexes
        if (i1 == i2) {
            if (i1 == 0) {
                if (jobsToShuffle.size() > 1) i2 += 1;
                else return; // No swap possible of only one job in scheduledTasks
            } else i1 -= 1;
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
        if (jobsToShuffle.size() > 0.2 * NR_OF_INITIAL_PLANNED) {
            operation_deleteJob(jobIndex);
        }
    }

    public static void localSwap() {
        int index1 = random.nextInt(jobsToShuffle.size());
        int index2;
        if (index1 == 0) index2 = 1;
        else index2 = index1 - 1;
        operation_swapJobs(index1, index2);
    }

    public static void smartInsert() {
        int jobIndex = random.nextInt(jobsToShuffle.size());
        if (!waitingJobs.isEmpty()) {
            int waitingIndex = random.nextInt(waitingJobs.size());
            operation_insertJob(jobIndex, waitingJobs.get(waitingIndex));
        }
    }

    public static void moveJob() {
        int i = getRandomScheduledJobIndex();
        if (i != -1) {
            Job j = jobsToShuffle.get(i);
            int t = j.getStartDate();
            int index = i + 1;
            while (t < j.getDueDate()) {
                t += jobsToShuffle.get(index).getDuration();
                if (index < jobsToShuffle.size() - 1) index++;
                else t = j.getDueDate();
            }
            operation_deleteJob(i);
            operation_insertJob(index, j);
        }
    }

    public static void removeJobHighestRejectionPenalty() {
        operation_deleteJob(highestEarlinessPenalty());
    }

    public static void ruinAndRecreate(int startIndex) {
        if (jobsToShuffle.size() > 15) {
            int endIndex = randomRuinSpanMild(startIndex);
            if (endIndex < startIndex) {
                int temp = endIndex;
                endIndex = startIndex;
                startIndex = temp;
            }
            ArrayList<Job> removed = new ArrayList<>();
            for (int i = startIndex; i < endIndex; i++) {
                removed.add(jobsToShuffle.get(i));
            }
            for (Job job : removed) {
                jobsToShuffle.remove(job);
            }
            for (int i = startIndex; i < endIndex; i++) {
                int jobNr = random.nextInt(removed.size());
                jobsToShuffle.add(i, removed.get(jobNr));
                removed.remove(jobNr);
            }
        }
    }

    private static int randomRuinSpanMild(int jobIndex) {
        int endIndex, span, size = jobsToShuffle.size();
        if (jobIndex < 0.7 * size) {
            span = random.nextInt((int) (0.3 * size));
        } else if (jobIndex < 0.8 * size) {
            span = random.nextInt((int) (0.2 * size));
        } else if (jobIndex < 0.9 * size) {
            span = random.nextInt((int) (0.1 * size));
        } else {
            span = random.nextInt(jobsToShuffle.size() - jobIndex);
            jobIndex = jobIndex - span;
        }
        endIndex = jobIndex + span;
        if (endIndex < 0) {
            endIndex = 0;
        }
        return endIndex;
    }
    /*********************************** ADVANCED OPERATIONS ***********************************/


    /*********************************** MAKE FEASIBLE ***********************************/
    public static void makeFeasibleSolution() {
        // TODO herbereken maar vanaf bepaalde index
        // Clear old schedule
        scheduledTasks.clear();

        boolean maxReached = false;

        for (Job job : jobsToShuffle) {
            if (!maxReached) {
                boolean succesfull = scheduleJob(job);
                if (job.getFinishDate() > horizon && succesfull) {
                    trim();
                } else if (job.getFinishDate() == horizon) {
                    maxReached = true;
                }
            } else {
                queueJob(job);
            }
        }
        for (Job job : toRemove) jobsToShuffle.remove(job);
    }

    private static boolean scheduleJob(Job job) {
        // No setup for first job
        if (scheduledTasks.isEmpty()) {
            int startDate = job.getReleaseDate();
            startDate = calculateFittingStartTimeUPs(startDate, job);
            if (job.getScheduledCostTemp(startDate) < job.getRejectionPenalty()) {
                planAndPlaceJob(job, startDate);
                return true;
            } else {
                queueJob(job);
                return false;
            }
        }
        // All others require a setup
        else {
            Task previous = scheduledTasks.getLast();
            assert previous instanceof Job : "Laatste item is geen job";

            // Schedule setup
            int startDateSetup = previous.finishDate + 1;
            Setup setup = setups.getSetup((Job) previous, job);
            startDateSetup = calculateFittingStartTimeUPs(startDateSetup, setup);

            // Schedule job
            int startDateJob = Math.max(job.getReleaseDate(), startDateSetup + setup.getDuration());
            startDateJob = calculateFittingStartTimeUPs(startDateJob, job);

            if (job.getScheduledCostTemp(startDateJob) < job.getRejectionPenalty()) {
                planAndPlaceJobWithSetup(setup, startDateSetup, job, startDateJob);
                return true;
            } else {
                queueJob(job);
                return false;
            }
        }
    }

    public static void trim() {
        Job lastJob = (Job) scheduledTasks.getLast();
        while (lastJob.getStartDate() > horizon) {
            scheduledTasks.removeLast();
            scheduledTasks.removeLast();
            queueJob(lastJob);
            lastJob = (Job) scheduledTasks.getLast();
        }
    }

    private static void queueJob(Job job) {
        waitingJobs.add(job);
        toRemove.add(job);
        job.setJobSkipped();
    }

    private static int calculateFittingStartTimeUPs(int startDate, Task task) {
        int finishDate = startDate + task.getDuration() - 1;
        for (UnavailablePeriod up : unavailablePeriods) {
            if (finishDate < up.getStartDate()) break;
            else startDate = Math.max(up.getFinishDate() + 1, startDate);
        }
        return startDate;
    }

    public static void planAndPlaceJob(Job job, int startDate) {
        if (job.makesDueDate(startDate)) {
            job.setStartDate(startDate);
            job.calculateFinishDate();
            scheduledTasks.add(job);
        } else {
            queueJob(job);
        }
    }

    private static void planAndPlaceJobWithSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
        if (job.makesDueDate(startingDateJob)) {
            setup.setStartDate(startingDateSetup);
            setup.calculateFinishDate();
            scheduledTasks.add(setup);

            job.setStartDate(startingDateJob);
            job.calculateFinishDate();
            scheduledTasks.add(job);
        } else {
            queueJob(job);
        }
    }
    /*********************************** MAKE FEASIBLE ***********************************/


    /*********************************** INVERSE FEASIBLE ***********************************/
    public static void inverseMakeFeasibleSolution() {
        scheduledTasks.clear();
        Collections.reverse(jobsToShuffle);

        boolean zeroReached = false;

        for (Job job : jobsToShuffle) {
            if (!zeroReached) {
                boolean succesfull = inverseScheduleJob(job);
                if (job.getStartDate() < 0 && succesfull) {
                    inverseTrim();
                    zeroReached = true;
                }
            } else {
                queueJob(job);
            }
        }
        for (Job job : toRemove) jobsToShuffle.remove(job);
        Collections.reverse(jobsToShuffle);
    }

    public static boolean inverseScheduleJob(Job job) {
        // No setup after last job
        if (scheduledTasks.isEmpty()) {
            int startDate = job.getLatestStartDate();
            startDate = inverseCalculateFittingStartTimeUPs(startDate, job);
            if (job.getScheduledCostTemp(startDate) < job.getRejectionPenalty()) {
                inversePlanAndPlaceJob(job, startDate);
                return true;
            } else {
                queueJob(job);
                return false;
            }

        }
        // All others require a setup
        else {
            Task previous = scheduledTasks.getFirst();
            assert previous instanceof Job : "Eerste item is geen job";

            // Schedule setup
            Setup setup = setups.getSetup(job, (Job) previous);
            int startDateSetup = previous.startDate - setup.getDuration();
            startDateSetup = inverseCalculateFittingStartTimeUPs(startDateSetup, setup);

            // Schedule job
            int startDateJob = Math.min(job.getLatestStartDate(), startDateSetup - job.getDuration() - 1);
            startDateJob = inverseCalculateFittingStartTimeUPs(startDateJob, job);

            if (job.getScheduledCostTemp(startDateJob) < job.getRejectionPenalty()) {
                inversePlanAndPlaceJobWithSetup(setup, startDateSetup, job, startDateJob);
                return true;
            } else {
                queueJob(job);
                return false;
            }
        }

    }

    public static void inverseTrim() {
        Job firstJob = (Job) scheduledTasks.getFirst();
        while (firstJob.getStartDate() < 0) {
            if (scheduledTasks.size() > 1) scheduledTasks.removeFirst();
            scheduledTasks.removeFirst();
            queueJob(firstJob);
            firstJob = (Job) scheduledTasks.getFirst();
        }
    }

    private static int inverseCalculateFittingStartTimeUPs(int startDate, Task task) {
        for (UnavailablePeriod up : inverseUnavailablePeriods) {
            if (startDate > up.getFinishDate()) break;
            else startDate = Math.min(up.getStartDate() - task.getDuration(), startDate);
        }
        return startDate;
    }

    public static void inversePlanAndPlaceJob(Job job, int startDate) {
        if (job.makesReleaseDate(startDate) && job.makesHorizon(startDate, horizon)) {
            job.setStartDate(startDate);
            job.calculateFinishDate();
            scheduledTasks.add(0, job);
        } else {
            queueJob(job);
        }
    }

    private static void inversePlanAndPlaceJobWithSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
        if (job.makesReleaseDate(startingDateJob)) {
            setup.setStartDate(startingDateSetup);
            setup.calculateFinishDate();
            scheduledTasks.add(0, setup);

            job.setStartDate(startingDateJob);
            job.calculateFinishDate();
            scheduledTasks.add(0, job);
        } else {
            queueJob(job);
        }
    }
    /*********************************** INVERSE FEASIBLE ***********************************/


    /*********************************** ASSISTING FUNCTIONS ***********************************/
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
            duration = (scheduledTasks.getLast().getFinishDate() - scheduledTasks.getFirst().getStartDate() + 1) * weight;
        if (print) {
            System.out.println("earliness: " + earliness);
            System.out.println("rejection: " + rejection);
            System.out.println("duration: " + duration);
        }
        return (double) Math.round((earliness + rejection + duration) * 100) / 100;
    }

    public static double calculateRejectionCost() {
        double cost = 0;
        for (Job j : allJobs) {
            if (j.getStartDate() < 0) {
                cost += j.getCost();
            }
        }
        return cost;
    }

    public static double calculateDurationCost() {
        double cost = 0;
        if (!scheduledTasks.isEmpty()) // add total schedule duration
            cost = (scheduledTasks.getLast().getFinishDate() - scheduledTasks.getFirst().getStartDate() + 1) * weight;
        return (double) Math.round(cost * 100) / 100;
    }
    public static LinkedList<Task> deepClone(LinkedList<Task> tasks) {
        LinkedList<Task> clone = new LinkedList<>();
        for (Task t : tasks) clone.add(t.clone());
        return clone;
    }

    public static LinkedList<Job> deepCloneJobs(LinkedList<Job> jobs) {
        LinkedList<Job> clone = new LinkedList<>();
        for (Job job : jobs) clone.add(job.clone());
        return clone;
    }
    
    public static int getRandomScheduledJobIndex() {
        Random r = new Random();
        int jobIndex = r.nextInt(jobsToShuffle.size() - 1);
        int i = jobIndex;
        while (i < jobsToShuffle.size() - 1) {
            if (scheduledTasks.contains(jobsToShuffle.get(i))) return i;
            i++;
        }
        i = 0;
        while (i < jobIndex) {
            if (scheduledTasks.contains(jobsToShuffle.get(i))) return i;
            i++;
        }
        return -1;
    }

    public static int highestEarlinessPenalty() {
        double cost = 0;
        int i = -1;
        for (Job j : jobsToShuffle) {
            if (j.getStartDate() >= 0 && j.getCost() > cost) {
                cost = j.getCost();
                i = jobsToShuffle.indexOf(j);
            }
        }
        return i;
    }
    /*********************************** ASSISTING FUNCTIONS ***********************************/
}

