import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class main {
    public static void main(String[] args) {

        // Reading from json file;
        try {
            String jsonString = Files.readString(Paths.get("datasets/TOY-20-10.json"));
            Gson gson = new Gson();
            InputData inputData = gson.fromJson(jsonString, InputData.class);
            System.out.println(inputData);
            int[][] matrix = inputData.getSetupMatrix();
            printMatrix(matrix);

        }
        catch (IOException e) {e.printStackTrace();}

    }

    public static void printMatrix(int[][] matrix){
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }


}


