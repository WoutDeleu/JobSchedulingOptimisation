import java.util.ArrayList;

public class SetupList {
    private ArrayList<int[]> setups = new ArrayList<>();

    public SetupList(ArrayList<int[]> setups) {
        this.setups = setups;
    }

    public Setup getSetup(int id1, int id2) {
        int duration = setups.get(id1)[id2];
        return new Setup(duration, id1, id2);
    }

    @Override
    public String toString() {
        return "SetupList{" +
                "setups=" + setups +
                '}';
    }
}
