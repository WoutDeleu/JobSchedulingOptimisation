import java.util.ArrayList;
import java.util.List;

public class SetupList {
    public void clear() {
        setups.clear();
    }
    private ArrayList<int[]> setups = new ArrayList<>();

    public SetupList(ArrayList<int[]> setups) {
        this.setups = setups;
    }

    public Setup getSetup(Job j1, Job j2) {
        int duration = setups.get(j1.getId())[j2.getId()];
        Setup setup = new Setup(duration, j1.getId(), j2.getId());
        // Plan setup as close as possible to job 2
        setup.calculateStartAndFinish(j2);
        return setup;
    }

    @Override
    public String toString() {
        return "SetupList{" +
                "setups=" + setups +
                '}';
    }
}
