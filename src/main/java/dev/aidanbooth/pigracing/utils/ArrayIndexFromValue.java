package dev.aidanbooth.pigracing.utils;

import dev.aidanbooth.pigracing.race.Racer;

import java.util.List;

public class ArrayIndexFromValue {
    public static int getIndex(List<Racer> racers, Racer racer) {
        int i = 1;
        for(Racer r : racers) {
            if(r == racer) {
                return i;
            }
            i++;
        }
        return 0;
    }
}
