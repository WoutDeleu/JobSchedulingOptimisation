/*
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class MAIN_TO_SUBMIT {
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

    // Parameters
    private static final int NR_OF_ITERATIONS_BEFORE_CALCULATE = 1; // Getest: beter bij kleine waarde
    private static int NR_OF_INITIAL_PLANNED;
    private static final int NR_ITERATIONS_BEFORE_ACCEPT = 15;//TODO: nog wat zoeken naar ideale waarden,
    // voor A-100_30 meer iteraties en kleinere marge is beter
    // voor B-400-90 grotere marge is beter
    //zeer sterk afhankelijk van random die operatie kiest imo
    //met zelfde parameters run1: kost= 1003000 run2: kost=880000 (dataset B-400-90 tijd = 1min)
    private static final double MARGIN = 1.001;
    private static long availableTime; // 10 min
    //    private static final long availableTime = 1000*60; // 1 min
    private static String current_name = "";

    private static int NR_OF_THREADS;
    private static Random random;


    public static void main(String[] arguments) {
        String inputFile_name = arguments[0];
        String sollutionFile_name = arguments[1];
        random = new Random(Integer.parseInt(arguments[2]));
        availableTime = Long.parseLong(arguments[3])*1000;
        int NR_OF_THREADS = Integer.parseInt(arguments[4]);

        InputData inputData = InputData.readFile(inputFile_name);
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

        // Write to JSON-file
        OutputData outputData = InputData.generateOutput(inputData.getName(), currentValue, scheduledTasks);
        InputData.writeFile(sollutionFile_name, outputData);
    }
    public static void localSearch() {
        int iterationCount = 0;
        long timeStart = System.currentTimeMillis();
        long timeNow = timeStart;


        LinkedList<Task> oldScheduling;
        LinkedList<Job> oldWaitingJobs;
        LinkedList<Job> oldJobsToShuffle;

        while (timeNow<timeStart+availableTime) {
            // Save the current situation
            oldScheduling = deepClone(scheduledTasks);
            oldWaitingJobs = deepCloneJobs(waitingJobs);
            oldJobsToShuffle = deepCloneJobs(jobsToShuffle);

            // Do some operations and make the result feasible
            for (int i = 0; i < NR_OF_ITERATIONS_BEFORE_CALCULATE; i++) {
                executeRandomBasicOperation();
                executeRandomIntelligentOperation();
            }

            makeFeasibleSolution();
            // Replace the current solution if the new solution scores better
            // Else reset the old solution
            double tempCost = calculateCost(false);
            if (tempCost < currentValue) {
                iterationCount = 0;
                long time = (timeNow-timeStart)/1000;
//                System.out.println("Verbetering gevonden! Cost: "+tempCost+", na "+time+" seconden");
                currentValue = tempCost;
            } else if(iterationCount > NR_ITERATIONS_BEFORE_ACCEPT && tempCost<MARGIN*currentValue && timeNow/1000 <((timeStart+availableTime)/1000)-2) {
                iterationCount = 0;
                long time = (timeNow-timeStart)/1000;
//                System.out.println("slechtere opl geaccepteerd! Cost: "+tempCost+", na "+time+" seconden");
                currentValue = tempCost;
            } else {
                scheduledTasks = oldScheduling;
                waitingJobs = oldWaitingJobs;
                jobsToShuffle = oldJobsToShuffle;
            }


//            printScheduledTasks("local search, cost="+currentValue);
            timeNow = System.currentTimeMillis();
            iterationCount++;
        }
    }
}
*/
