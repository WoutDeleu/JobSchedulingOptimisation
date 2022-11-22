import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class basicFunctionsTest {

    // Testdata
    LinkedList<Task> scheduledTasks = new LinkedList<>();

    Job j0 = new Job(0,38,552,936,0.493,852.89);
    Job j1 = new Job(1,39,232,912,0.277,887.786);
    Job j2 = new Job(2,29,309,1092,0.62,2337.4);
    Job j3 = new Job(3,29,309,1092,0.62,2337.4);

    Setup s00 = new Setup(0,0,0);
    Setup s10 = new Setup(2,1,0);
    Setup s20 = new Setup(4,2,0);
    Setup s30 = new Setup(4,3,0);
    Setup s01 = new Setup(2,0,1);
    Setup s11 = new Setup(0,1,1);
    Setup s21 = new Setup(4,2,1);
    Setup s31 = new Setup(2,3,1);
    Setup s02 = new Setup(4,0,2);
    Setup s12 = new Setup(4,1,2);
    Setup s22 = new Setup(0,2,2);
    Setup s32 = new Setup(6,3,2);
    Setup s03 = new Setup(4,0,3);
    Setup s13 = new Setup(2,1,3);
    Setup s23 = new Setup(6,2,3);
    Setup s33 = new Setup(0,3,3);

    void generateTaskList() {
        scheduledTasks.clear();
        scheduledTasks.add(j0);
        scheduledTasks.add(s01);
        scheduledTasks.add(j1);
        scheduledTasks.add(s12);
        scheduledTasks.add(j2);
        scheduledTasks.add(s23);
        scheduledTasks.add(j3);
    }

    void readDataInMain() {
        InputData inputData = InputData.readFile("datasets/eigen_dataset.json");
        SetupList setups = inputData.generateSetupList();
        List<Job> jobs = inputData.getJobsSortedReleaseDate();
        List<UnavailablePeriod> unavailablePeriods = inputData.getUnavailablePeriods();
        main.calculateInitialSolution(setups, jobs, unavailablePeriods);
    }

    @Test
    void deleteJobInTheMiddle() {
        generateTaskList();
        readDataInMain();
        main.operation_deleteJob(2); // delete j1
        LinkedList<Task> result = new LinkedList<>();
        result.add(j0); result.add(s02); result.add(j2); result.add(s23); result.add(j3);
        Assertions.assertEquals(main.scheduledTasks, result);
    }
}